# Neat F2P

Open-source codebase for the Neat F2P project, an unreleased RSC private server featuring authentic, F2P-only gameplay.

https://neatf2p-nextjs.vercel.app/

## Differences From Open RSC's Core Framework

-   `member_world` = `false`
-   `can_feature_membs` = `false`
-   `MAX_PLAYERS_PER_IP` = `2`
-   The `players` table has a new column, `websiteUserId` for linking players to website accounts.
