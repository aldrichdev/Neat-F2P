# Neat F2P

Open-source codebase for the Neat F2P project, an unreleased RSC private server featuring authentic, F2P-only gameplay.

https://neatf2p-nextjs.vercel.app/

## Info

-   Based on Open RSC's Core Framework with Few or No Added Changes
-   Open Source (Game & Website) Forever
-   F2P Mode Enabled (Forever) - Only F2P Areas, Features, Items, Quests, Spells, Prayers, Etc.
-   1 Page Bank (Authentic F2P Behavior)
-   1x EXP Rates
-   Play with RSC+ Client
-   Skip Tutorial Option Enabled
-   Max 2 Characters Logged In At Once
-   No QOL - Straight RSC
-   No Global Chat or Kill Feed
-   No Transfers From Other Servers
-   No Cheating
-   Aiming for a March 2024 Release

## Differences From Open RSC's Core Framework

-   `server_name` is customized
-   `member_world` = `false`
-   `can_feature_membs` = `false`
-   `MAX_PLAYERS_PER_IP` = `2`
-   `shuffle_pid_order` = `false`
-   `want_packet_register` = `false` (in-game characters are created on our site)
-   The `players` table has a new column, `websiteUserId` for linking players to website accounts.

## Tribute

-   Logg, for helping with my questions
-   My friends - Sno, Zeph, Zhiguli, Bank Greeter, Komis and others - for showing so much interest in the project early on.
-   My YouTube subscribers, for helping with RSC F2P history questions.
-   Jagex, for releasing RuneScape with a F2P mode.
