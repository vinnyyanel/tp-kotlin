# CLAUDE.md — Cahier des charges : Application de messagerie Kotlin/Android

## ⏰ CONTEXTE CRITIQUE
Projet académique Master 2 IIRT (ESGIS Gabon) — **PRÉSENTATION DEMAIN**.
Priorité absolue : une app qui COMPILE et fonctionne en démo. Pragmatisme > perfection.
Toujours privilégier la solution la plus simple qui remplit le critère du sujet.

## 1. Objectif du projet
Application mobile Android complète en Kotlin, type WhatsApp, permettant de converser :
- avec d'autres personnes en temps réel (mode humain)
- avec un agent IA conversationnel (mode IA)
Le tout dans une interface unifiée (une seule liste de conversations).

## 2. Stack technique (DÉCISIONS DÉJÀ VALIDÉES — ne pas remettre en question)
- **Langage** : Kotlin, UI en **Jetpack Compose** (Material 3)
- **Architecture** : MVVM + Repository pattern (Clean Architecture allégée)
- **Backend** : **Supabase** (plan gratuit) — Auth, Postgres, Realtime, Storage
  - Librairie : `supabase-kt` v3 (BOM `io.github.jan-tennert.supabase:bom:3.0.1`)
  - Le schéma SQL complet existe déjà : voir `schema.sql` (tables profiles,
    conversations, participants, messages + RLS + Realtime + bucket 'media')
- **Notifications push** : FCM seul (projet Firebase coquille vide, gratuit),
  déclenché par un Database Webhook ou Edge Function Supabase → SI LE TEMPS MANQUE,
  fallback acceptable : notification locale quand l'app reçoit un message via Realtime
- **IA** : API **Gemini** (tier gratuit, modèle `gemini-3-flash` ou équivalent Flash
  disponible), appels REST en streaming SSE via Ktor Client
- **Stockage local chiffré** : Room + SQLCipher, OU DataStore + EncryptedSharedPreferences
  si plus rapide à mettre en place
- **Chiffrement bout en bout** : version pédagogique simple — X25519 (échange de clés)
  + AES-GCM par conversation, clés dans Android Keystore. NE PAS implémenter Signal.
- **DI** : objets singleton simples (pas de Hilt si ça fait perdre du temps)

## 3. Fichiers déjà écrits (à intégrer tels quels, corriger si besoin)
- `schema.sql` — schéma Postgres complet avec RLS (déjà exécuté ou à exécuter
  dans le SQL Editor Supabase)
- `di/SupabaseModule.kt` — client Supabase singleton (URL et clé anon à remplir)
- `data/Models.kt` — data classes sérialisables Profile, Conversation, Message
- `data/ChatRepository.kt` — auth, historique, envoi, accusés de lecture,
  upload médias, Flow temps réel via postgresChangeFlow

## 4. Fonctionnalités à livrer (ordre de priorité pour la démo)
### P0 — indispensable pour la démo
1. Inscription / connexion (email + mot de passe, Supabase Auth)
2. Liste des conversations (triée par last_message_at) avec indicateur visuel
   distinguant contact humain / agent IA (badge ou icône robot)
3. Écran de chat : envoi/réception de messages texte en TEMPS RÉEL (Realtime)
4. Contact spécial « Assistant IA » : réponses de Gemini en STREAMING
   (affichage progressif token par token dans la bulle)
5. Choix du personnage IA : sélecteur (généraliste / juridique / technique)
   = system prompts différents. Historique envoyé à chaque appel (N derniers messages)
6. Accusés de réception : ✓ envoyé / ✓✓ lu (champ status)

### P1 — si le temps le permet
7. Envoi de photos (Storage bucket 'media' + Coil pour l'affichage)
8. Messages audio (MediaRecorder → upload Storage)
9. Notifications (FCM ou local en fallback)
10. Stockage local chiffré (cache Room des messages)
11. Chiffrement E2E simplifié

### P2 — bonus rapport
12. Tests unitaires ViewModels (JUnit + MockK + Turbine)
13. ktlint/detekt

## 5. Écrans Compose à créer
- `AuthScreen` (connexion/inscription, un seul écran avec toggle)
- `ConversationsScreen` (liste + FAB nouvelle conversation + bouton chat IA)
- `ChatScreen` (bulles alignées gauche/droite, champ de saisie, bouton média,
  indicateur streaming IA "en train d'écrire...")
- `PersonaPickerDialog` (choix du personnage IA)
- Navigation : Navigation Compose, 3 destinations

## 6. Intégration Gemini (mode IA)
- Endpoint : `https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse&key={API_KEY}`
- Clé API : à créer sur aistudio.google.com (gratuit, sans CB) — variable
  dans `local.properties`, JAMAIS commitée
- System prompts par personnage, ex :
  - general : "Tu es un assistant généraliste amical et concis."
  - juridique : "Tu es un assistant juridique spécialisé en droit. Tu rappelles
    que tes réponses ne remplacent pas un avocat."
  - technique : "Tu es un assistant technique expert en développement."
- Les messages IA sont stockés dans `messages` avec `is_from_ai = true`,
  `sender_id = null` → l'historique est conservé naturellement (exigence du sujet)
- Rate limit free tier ~10 req/min : gérer les erreurs 429 avec message utilisateur

## 7. Exigences de livrables du sujet (à respecter)
- **Git** : une branche feature par fonctionnalité majeure
  (ex: feature/auth, feature/realtime-chat, feature/ai-agent, feature/media),
  merge dans main. Commits atomiques avec messages clairs.
- **Documentation** : diagrammes UML (cas d'utilisation, séquence pour l'envoi
  de message temps réel et pour le flux IA streaming, diagramme de classes)
  — générer en PlantUML ou Mermaid dans /docs
- **Rapport architectural** : justifier chaque choix (déjà argumenté :
  Supabase = Postgres relationnel + RLS + gratuit sans CB vs Firebase Storage
  payant depuis fév. 2026 ; Gemini = seul tier gratuit permanent ; MVVM = testabilité)
- Backend "déployé et accessible" = le projet Supabase cloud (rien à héberger)
- Qualité : couverture de tests unitaires Kotlin Test/JUnit, règles Lint

## 8. Configuration requise (à faire par le développeur humain)
1. Créer projet sur supabase.com → exécuter schema.sql dans SQL Editor
2. Récupérer URL + anon key → SupabaseModule.kt (ou local.properties)
3. Créer clé API Gemini sur aistudio.google.com → local.properties
4. (P1) Créer projet Firebase vide pour FCM → google-services.json

## 9. Contraintes de code
- minSdk 26, targetSdk 35, Kotlin 2.0+, AGP récent
- kotlinx-serialization pour le JSON
- Coroutines + Flow partout (pas de callbacks)
- Gestion d'erreurs : try/catch dans les repositories, états UI
  sealed class (Loading / Success / Error) dans les ViewModels
- Ne jamais mettre de secrets en dur dans le code commité
