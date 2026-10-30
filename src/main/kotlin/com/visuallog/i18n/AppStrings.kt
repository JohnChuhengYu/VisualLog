package com.visuallog.i18n

interface AppStrings {
    // Header & General
    val appName: String
    val back: String
    val save: String
    val export: String
    val done: String
    val edit: String
    val close: String
    val cancel: String
    val delete: String
    val daysCount: (Int) -> String
    val myJournal: String

    // Grid & Timeline
    val editBackground: String
    val doneEditing: String
    val selectImage: String

    // Whiteboard Tools
    val pen: String
    val eraser: String
    val sweep: String
    val select: String
    val addSticker: String
    val size: String
    val layer: String
    val layerTop: String
    val layerMid: String
    val layerBot: String

    // Sticker Box
    val stickerBox: String
    val tabEmoji: String
    val tabRecent: String
    val importPhoto: String
    val noRecentStickers: String

    // Text Journal
    val dailyEntry: String
    val writeYourStory: String
    val mood: String
    val weather: String

    // Export Modal
    val exportCardTitle: String
    val polaroidCardBadge: String
    val copiedToClipboard: String
    val readyToShare: String
    val shareSubtitle: String
    val showInFinder: String
    val showInFolder: String
    val saveImageToFile: String
    val copyAgain: String

    // Language Switcher
    val language: String
    val followSystem: String
}
