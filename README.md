<h1 align="center">🏰 La Citadelle - Vote App</h1>

<p align="center">
  <img src="app/src/main/res/drawable/logo_citadelle.png" alt="Logo de La Citadelle" width="160"/>
</p>

<p align="center">
  <b>Application Android officielle pour voter sur le serveur Minecraft <a href="https://lacitadelle-mc.fr">La Citadelle</a></b><br/>
  Soutenez le royaume, renforcez les murs et aidez votre cité à prospérer ⚔️
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9%2B-purple?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Android-10%2B-brightgreen?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Gradle-8.4+-green?logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="License"/>
  <img src="https://img.shields.io/github/last-commit/Eldrazy-Git/La-Citadelle---Vote-app" alt="Dernier commit"/>
</p>

---

## ⚔️ Présentation

**La Citadelle Vote App** est une application Android conçue pour les joueurs du serveur Minecraft [La Citadelle](https://lacitadelle-mc.fr).  
Elle permet de **voter sur plusieurs sites** en un seul endroit, tout en respectant les **temps de cooldown** entre deux votes.

Inspirée de l’univers médiéval du serveur, elle propose une **interface élégante et fonctionnelle**, fidèle à l’identité visuelle du royaume 🛡️

---

## 🧩 Fonctionnalités

### 🕓 Gestion des votes
- Intégration de **plusieurs sites de vote** avec logos et liens directs  
- Affichage en **temps réel du cooldown restant**  
- Lancement automatique du compte à rebours après un vote  
- Gestion intelligente : le timer ne se relance **que** lorsqu’un vote est réellement effectué  

### 🔔 Notifications personnalisées
- Notifications **programmées** via WorkManager  
- Sons et polices **personnalisés**  
- Fonctionnement **même en arrière-plan ou après redémarrage**  

### ⚙️ Persistance des données
- Sauvegarde des timers et des sites de vote via **DataStore Preferences**  
- Rappels automatiques pour chaque site de vote  
- Annulation des notifications à l’ouverture de l’app  

### 🎨 Thème médiéval
- Couleurs : `#283852` (fond), `#40516d` (boutons), `#aba36d` (bordures)  
- Police : **MedievalSharp Bold**  
- Interface fidèle au site [La Citadelle](https://lacitadelle-mc.fr)

---

## 🛠️ Architecture du projet

| Fichier / Module | Description |
|------------------|-------------|
| `MainActivity.kt` | Écran principal, boutons de vote, timers et logique d’ouverture des liens |
| `VoteScheduler.kt` | Gestion des timers via WorkManager |
| `NotificationHelper.kt` | Création et affichage des notifications |
| `VoteSitesRepository.kt` | Persistance et gestion des temps de cooldown |
| `VoteReminderWorker.kt` | Gestion des rappels automatiques (même après reboot) |

---

## 💾 Installation

### 🔧 Installation manuelle
1. Téléchargez la dernière version depuis l’onglet **[Releases](https://github.com/Eldrazy-Git/La-Citadelle---Vote-app/releases)**.  
2. Sur votre téléphone Android :
   - Activez les **sources inconnues** si nécessaire  
   - Installez le fichier `.apk` téléchargé  
   - Validez les permissions lors du premier lancement  

> ⚠️ Le message “cette application peut contenir des virus” est affiché par Android pour toute app installée manuellement (hors Play Store).  
> L’application est **sécurisée et signée**.

---

## 🧙‍♂️ Développement

### Environnement
- **Android Studio** : Ladybug 🐞 (2024.3.2 Patch 1)  
- **Kotlin** : 1.9+  
- **Gradle** : 8.4+  
- **Min SDK** : 29 (Android 10)  
- **Target SDK** : 35 (Android 15)

### Build (version Release)
```bash
Build → Generate Signed App Bundle / APK → APK (Release)
