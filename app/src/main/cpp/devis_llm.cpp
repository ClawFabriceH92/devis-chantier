// Pont JNI entre l'application et llama.cpp pour la dictée assistée « sur le
// téléphone » (§4.4). Volontairement petit : un moteur = un modèle + un
// contexte, une génération à la fois, sans mémoire de conversation. L'ordre des
// appels est garanti côté Kotlin (LlmLocal, un seul thread).
//
// Le modèle est un fichier GGUF (LFM3-1.7B de Liquid AI) téléchargé par
// l'artisan. La sortie est contrainte par une grammaire GBNF : le modèle ne
// peut produire qu'un tableau JSON de lignes de devis, jamais du texte libre.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

#define TAG "DevisLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int TAILLE_LOT = 512;

struct Moteur {
    llama_model *            model = nullptr;
    llama_context *          ctx   = nullptr;
    common_chat_templates_ptr gabarits;
    std::atomic<bool>        annuler{false};
    int                      n_ctx = 0;
    std::string              derniere_erreur;
};

void journal_llama(ggml_log_level niveau, const char * texte, void * /*inutile*/) {
    if (!texte || !*texte) return;
    int prio = ANDROID_LOG_DEBUG;
    if (niveau == GGML_LOG_LEVEL_ERROR) prio = ANDROID_LOG_ERROR;
    else if (niveau == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    else if (niveau == GGML_LOG_LEVEL_INFO) prio = ANDROID_LOG_INFO;
    __android_log_write(prio, "llama.cpp", texte);
}

std::string vers_std(JNIEnv * env, jstring s) {
    if (s == nullptr) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

// NewStringUTF plante sur une séquence UTF-8 invalide : on coupe ce qui ne
// forme pas un caractère complet (un token peut s'arrêter au milieu d'un « é »).
std::string utf8_propre(const std::string & s) {
    std::string r;
    r.reserve(s.size());
    size_t i = 0;
    const auto * b = reinterpret_cast<const unsigned char *>(s.data());
    while (i < s.size()) {
        unsigned char c = b[i];
        size_t n = 0;
        if (c < 0x80) n = 1;
        else if ((c & 0xE0) == 0xC0) n = 2;
        else if ((c & 0xF0) == 0xE0) n = 3;
        else if ((c & 0xF8) == 0xF0) n = 4;
        else { i++; continue; }  // octet de continuation orphelin : ignoré
        if (i + n > s.size()) break;  // séquence incomplète en fin de chaîne
        bool ok = true;
        for (size_t k = 1; k < n; k++) {
            if ((b[i + k] & 0xC0) != 0x80) { ok = false; break; }
        }
        if (ok) r.append(s, i, n);
        i += ok ? n : 1;
    }
    return r;
}

jstring vers_java(JNIEnv * env, const std::string & s) {
    return env->NewStringUTF(utf8_propre(s).c_str());
}

bool rappel_annulation(void * donnees) {
    return static_cast<Moteur *>(donnees)->annuler.load();
}

Moteur * moteur_de(jlong poignee) {
    return reinterpret_cast<Moteur *>(poignee);
}

// Décode des tokens par lots de TAILLE_LOT à partir de la position courante de
// la mémoire ; seul le dernier token demande des logits.
bool decoder(Moteur * m, const std::vector<llama_token> & tokens) {
    auto * mem = llama_get_memory(m->ctx);
    llama_pos pos = llama_memory_seq_pos_max(mem, 0) + 1;
    const int n = (int) tokens.size();
    for (int debut = 0; debut < n; debut += TAILLE_LOT) {
        if (m->annuler.load()) { m->derniere_erreur = "annulé"; return false; }
        const int fin = std::min(n, debut + TAILLE_LOT);
        common_batch lot(m->ctx);
        for (int i = debut; i < fin; i++) {
            lot.add(tokens[i], pos++, 0, i == n - 1);
        }
        const int rc = llama_process(m->ctx, LLAMA_PROCESS_TYPE_DECODE, lot.get());
        if (rc != 0) {
            m->derniere_erreur = rc == 2 ? "annulé" : ("échec du décodage (code " + std::to_string(rc) + ")");
            return false;
        }
    }
    return true;
}

} // namespace

extern "C" {

// Chargement des backends CPU (variantes selon le processeur) et initialisation.
JNIEXPORT jstring JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeInit(JNIEnv * env, jobject, jstring dossierLibs) {
    llama_log_set(journal_llama, nullptr);
    const std::string dossier = vers_std(env, dossierLibs);
    if (!dossier.empty()) {
        ggml_backend_load_all_from_path(dossier.c_str());
    }
    llama_backend_init();
    return vers_java(env, llama_print_system_info());
}

JNIEXPORT jlong JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeCharger(
        JNIEnv * env, jobject, jstring chemin, jint nThreads, jint nCtx) {
    const std::string fichier = vers_std(env, chemin);
    auto moteur = std::make_unique<Moteur>();

    llama_model_params pm = llama_model_default_params();
    pm.n_gpu_layers = 0;
    moteur->model = llama_model_load_from_file(fichier.c_str(), pm);
    if (moteur->model == nullptr) {
        LOGE("modèle illisible : %s", fichier.c_str());
        return 0;
    }

    llama_context_params pc = llama_context_default_params();
    pc.n_ctx = (uint32_t) std::max(512, (int) nCtx);
    pc.n_batch = TAILLE_LOT;
    pc.n_ubatch = TAILLE_LOT;
    pc.n_threads = std::max(1, (int) nThreads);
    pc.n_threads_batch = pc.n_threads;
    pc.no_perf = true;
    pc.abort_callback = rappel_annulation;
    pc.abort_callback_data = moteur.get();
    moteur->ctx = llama_init_from_model(moteur->model, pc);
    if (moteur->ctx == nullptr) {
        LOGE("contexte impossible (n_ctx=%d)", (int) pc.n_ctx);
        llama_model_free(moteur->model);
        return 0;
    }
    moteur->n_ctx = (int) llama_n_ctx(moteur->ctx);
    moteur->gabarits = common_chat_templates_init(moteur->model, "");

    char desc[256];
    llama_model_desc(moteur->model, desc, sizeof(desc));
    LOGI("modèle chargé : %s · %d threads · contexte %d", desc, pc.n_threads, moteur->n_ctx);
    return reinterpret_cast<jlong>(moteur.release());
}

JNIEXPORT jstring JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeDescription(JNIEnv * env, jobject, jlong poignee) {
    auto * m = moteur_de(poignee);
    if (m == nullptr || m->model == nullptr) return vers_java(env, "");
    char desc[256];
    llama_model_desc(m->model, desc, sizeof(desc));
    const double taille_gio = (double) llama_model_size(m->model) / (1024.0 * 1024.0 * 1024.0);
    char tampon[512];
    snprintf(tampon, sizeof(tampon), "%s · %.2f Gio · contexte %d · %d threads",
             desc, taille_gio, m->n_ctx, llama_n_threads(m->ctx));
    return vers_java(env, tampon);
}

// Une génération complète : gabarit de conversation du modèle (réflexion
// désactivée), prompt décodé, puis échantillonnage contraint par la grammaire.
JNIEXPORT jstring JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeGenerer(
        JNIEnv * env, jobject, jlong poignee,
        jstring systeme, jstring utilisateur, jstring grammaire, jint maxTokens) {
    auto * m = moteur_de(poignee);
    if (m == nullptr || m->ctx == nullptr) return nullptr;
    m->annuler.store(false);
    m->derniere_erreur.clear();

    common_chat_templates_inputs entrees;
    common_chat_msg msg_sys; msg_sys.role = "system"; msg_sys.content = vers_std(env, systeme);
    common_chat_msg msg_usr; msg_usr.role = "user";   msg_usr.content = vers_std(env, utilisateur);
    if (!msg_sys.content.empty()) entrees.messages.push_back(msg_sys);
    entrees.messages.push_back(msg_usr);
    entrees.add_generation_prompt = true;
    entrees.use_jinja = true;
    entrees.enable_thinking = false;  // LFM3 : « <think></think> » puis la réponse directement
    entrees.add_bos = false;          // le gabarit écrit déjà <|startoftext|>, reconnu comme token spécial

    std::string prompt;
    try {
        const auto params = common_chat_templates_apply(m->gabarits.get(), entrees);
        prompt = params.prompt.str();
    } catch (const std::exception & e) {
        m->derniere_erreur = std::string("gabarit de conversation : ") + e.what();
        LOGE("%s", m->derniere_erreur.c_str());
        return nullptr;
    }

    const auto tokens = common_tokenize(m->ctx, prompt, /*add_special*/ false, /*parse_special*/ true);
    const int n_prompt = (int) tokens.size();
    const int max_gen = std::max(16, (int) maxTokens);
    if (n_prompt + max_gen > m->n_ctx) {
        m->derniere_erreur = "dictée trop longue pour le modèle (" + std::to_string(n_prompt) + " tokens)";
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(m->ctx), true);
    if (!decoder(m, tokens)) {
        LOGE("prompt : %s", m->derniere_erreur.c_str());
        return nullptr;
    }

    common_params_sampling ech;
    ech.seed = 7;
    ech.temp = 0.1f;      // réglage recommandé par Liquid AI pour LFM3 (modèle de carte)
    ech.top_k = 50;
    ech.top_p = 1.0f;
    ech.min_p = 0.0f;
    ech.penalty_repeat = 1.0f;  // pas de pénalité : le JSON répète volontairement les mêmes clés
    const std::string gbnf = vers_std(env, grammaire);
    if (!gbnf.empty()) {
        ech.grammar = common_grammar(COMMON_GRAMMAR_TYPE_USER, gbnf);
    }
    common_sampler * echantillonneur = common_sampler_init(m->model, ech);
    if (echantillonneur == nullptr) {
        m->derniere_erreur = "grammaire refusée par llama.cpp";
        LOGE("%s", m->derniere_erreur.c_str());
        return nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(m->model);
    std::string sortie;
    bool ok = true;
    for (int n = 0; n < max_gen; n++) {
        if (m->annuler.load()) { m->derniere_erreur = "annulé"; ok = false; break; }
        const llama_token tok = common_sampler_sample(echantillonneur, m->ctx, -1);
        common_sampler_accept(echantillonneur, tok, true);
        if (llama_vocab_is_eog(vocab, tok)) break;
        sortie += common_token_to_piece(m->ctx, tok, /*special*/ false);
        if (!decoder(m, {tok})) { ok = false; break; }
    }
    common_sampler_free(echantillonneur);

    if (!ok) {
        LOGW("génération interrompue : %s", m->derniere_erreur.c_str());
        return nullptr;
    }
    LOGI("génération : %d tokens de prompt, %zu caractères produits", n_prompt, sortie.size());
    return vers_java(env, sortie);
}

JNIEXPORT jstring JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeDerniereErreur(JNIEnv * env, jobject, jlong poignee) {
    auto * m = moteur_de(poignee);
    return vers_java(env, m ? m->derniere_erreur : std::string("moteur absent"));
}

JNIEXPORT void JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeAnnuler(JNIEnv *, jobject, jlong poignee) {
    auto * m = moteur_de(poignee);
    if (m) m->annuler.store(true);
}

JNIEXPORT void JNICALL
Java_com_trucdecomptable_devischantier_ia_LlmNatif_nativeLiberer(JNIEnv *, jobject, jlong poignee) {
    auto * m = moteur_de(poignee);
    if (m == nullptr) return;
    m->gabarits.reset();
    if (m->ctx) llama_free(m->ctx);
    if (m->model) llama_model_free(m->model);
    delete m;
    LOGI("modèle libéré");
}

} // extern "C"
