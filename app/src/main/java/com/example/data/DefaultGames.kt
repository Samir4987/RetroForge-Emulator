package com.example.data

import com.example.data.model.RetroGame

object DefaultGames {
    val list: List<RetroGame> = listOf(
        RetroGame(
            id = "gb_pocket_quest",
            title = "Pocket Quest: 8-Bit Dungeon",
            system = "GB",
            developer = "RetroForge Studio",
            description = "Explore dungeon rooms, slay slimes and skeletons, collect gold, and solve puzzles with classic monochrome chiptune audio.",
            isBuiltIn = true,
            iconEmoji = "🗡️",
            releaseYear = "1989"
        ),
        RetroGame(
            id = "gbc_crystal_cavern",
            title = "Crystal Cavern Color",
            system = "GBC",
            developer = "RetroForge Studio",
            description = "Side-scrolling platformer with vibrant 15-bit color palettes, floating platforms, gems, and fluid jumping mechanics.",
            isBuiltIn = true,
            iconEmoji = "💎",
            releaseYear = "1998"
        ),
        RetroGame(
            id = "gba_advance_speedway",
            title = "Advance Speedway 32-Bit",
            system = "GBA",
            developer = "RetroForge Studio",
            description = "Mode 7 pseudo-3D perspective racer with curved scaling tracks, tachometer, turbo boosts, and competitor AI.",
            isBuiltIn = true,
            iconEmoji = "🏎️",
            releaseYear = "2001"
        ),
        RetroGame(
            id = "nds_dual_arena",
            title = "Dual Touch Arena",
            system = "NDS",
            developer = "RetroForge Studio",
            description = "Dual-screen experience. Top screen shows tactical 3D arena radar; bottom screen features interactive stylus touch defense.",
            isBuiltIn = true,
            iconEmoji = "📱",
            releaseYear = "2004"
        ),
        RetroGame(
            id = "n64_polystar",
            title = "PolyStar 64",
            system = "N64",
            developer = "RetroForge Studio",
            description = "3D polygon space flight simulation with 360-degree analog control, pitch/yaw rotation, target crosshairs, and 3D laser fire.",
            isBuiltIn = true,
            iconEmoji = "🚀",
            releaseYear = "1996"
        )
    )
}
