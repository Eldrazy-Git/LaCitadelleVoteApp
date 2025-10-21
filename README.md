# 🏰 La Citadelle - Vote App

![Logo de La Citadelle](app/src/main/res/drawable/logo_citadelle.png)

> Application Android officielle permettant aux joueurs du serveur **Minecraft La Citadelle** de voter facilement pour soutenir le royaume 🛡️

---

## ⚔️ Présentation

**La Citadelle Vote App** est une application Android développée sur mesure pour les joueurs du serveur Minecraft [La Citadelle](https://lacitadelle-mc.fr).  
Elle regroupe **tous les sites de vote officiels** du serveur, avec un design inspiré du thème médiéval du site web.

🎯 Objectif : simplifier le vote quotidien tout en rappelant automatiquement au joueur de voter grâce à des **notifications programmées intelligentes**.

---

## 🧩 Fonctionnalités principales

### 🕓 Gestion des votes
- 3 (ou plus) **sites de vote intégrés**
- Affichage en temps réel du **temps restant avant le prochain vote**
- Lancement automatique du **compte à rebours après un vote**
- **Boutons visuels** avec les logos officiels des sites de vote

### 🔔 Notifications personnalisées
- Rappels automatiques lorsque les votes redeviennent disponibles  
- Sons et polices personnalisées pour correspondre au thème du serveur  
- Fonctionne même **après redémarrage du téléphone**

### ⚙️ Système de persistance
- **Timers sauvegardés** localement avec `DataStore`  
- Gestion du **cooldown individuel** pour chaque site de vote  
- Les notifications se réinitialisent **uniquement** si tu votes réellement (ou via la notification)

### 🛡️ Design médiéval unique
- Palette de couleurs : `#283852`, `#40516d`, `#aba36d`  
- Police personnalisée : **MedievalSharp Bold**  
- Interface fidèle à l'identité visuelle du site officiel

---

## 💾 Fonctionnement interne

| Module | Description |
|--------|--------------|
| `MainActivity.kt` | Gère l’affichage principal, les boutons de vote et les timers. |
| `VoteScheduler.kt` | Programme les rappels de vote via **WorkManager**. |
| `NotificationHelper.kt` | Envoie les notifications personnalisées. |
| `VoteSitesRepository.kt` | Stocke les temps de cooldown et les prochaines échéances. |
| `VoteReminderWorker.kt` | Exécute les rappels même quand l’application est fermée. |

---

## 📱 Installation

### 🔧 Méthode manuelle (APK)
1. Télécharger la dernière version depuis l’onglet **Releases** du dépôt.  
2. Sur ton appareil Android :
   - Autorise les **sources inconnues** (une seule fois).
   - Ouvre le fichier `.apk`.
   - Valide l’installation.

> ⚠️ L’avertissement “cette application peut être dangereuse” est normal :  
> il s’affiche pour toute installation manuelle non issue du Play Store.

---

## 🧙‍♂️ Développement

### Environnement
- Android Studio Ladybug | 2024.3.2 Patch 1
- Kotlin 1.9+
- Gradle 8.4+
- Min SDK : Android 10 (API 29)
- Target SDK : Android 15 (API 35)

### Build
> Depuis Android Studio :  
`Build → Generate Signed App Bundle / APK → APK (Release)`  
La signature est gérée via un keystore local (non partagé).

---

## 📜 À propos

- 🧱 Projet : Application de vote pour le serveur Minecraft **La Citadelle**  
- 🌐 Site officiel : [https://lacitadelle-mc.fr](https://lacitadelle-mc.fr)  
- 💬 Discord : [https://discord.gg/h8jr9jkQzk](https://discord.gg/h8jr9jkQzk)  
- 👑 Développeur Android : **Eldrazy**

---

## 🧾 Licence

Ce projet est sous licence **MIT**.  
Vous êtes libres de réutiliser le code, à condition de **créditer l’auteur original** et **ne pas le distribuer sous le nom "La Citadelle"** sans autorisation.

---

## 🖼️ Aperçu

*(Tu peux ajouter ici des captures d’écran une fois l’appli en prod 👇)*

