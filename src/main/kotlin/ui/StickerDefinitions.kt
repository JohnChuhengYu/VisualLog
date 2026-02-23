package ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object StickerDefinitions {
    val WeatherOptions: Map<String, ImageVector> = mapOf(
        "SUNNY" to Icons.Default.WbSunny,
        "PARTLY_CLOUDY" to Icons.Default.WbCloudy,
        "CLOUDY" to Icons.Default.Cloud,
        "OVERCAST" to Icons.Default.CloudQueue,
        "RAINY" to Icons.Default.Grain,
        "HEAVY_RAIN" to Icons.Default.Umbrella,
        "THUNDERSTORM" to Icons.Default.FlashOn,
        "SNOWY" to Icons.Default.AcUnit,
        "FOGGY" to Icons.Default.BlurOn,
        "WINDY" to Icons.Default.Air,
        "HOT" to Icons.Default.Thermostat,
        "COLD" to Icons.Default.SevereCold,
        "NIGHT_CLEAR" to Icons.Default.NightsStay,
        "NIGHT_CLOUDY" to Icons.Default.Cloud // Fallback as CloudySnowing unavailable
    )

    val MoodOptions: Map<String, ImageVector> = mapOf(
        "HAPPY" to Icons.Default.SentimentSatisfied,
        "VERY_HAPPY" to Icons.Default.SentimentVerySatisfied,
        "NEUTRAL" to Icons.Default.SentimentNeutral,
        "SAD" to Icons.Default.SentimentDissatisfied,
        "VERY_SAD" to Icons.Default.SentimentVeryDissatisfied,
        "ANGRY" to Icons.Default.MoodBad,
        "EXCITED" to Icons.Default.Celebration,
        "TIRED" to Icons.Default.Bedtime,
        "SICK" to Icons.Default.Sick,
        "LOVED" to Icons.Default.Favorite,
        "CONFUSED" to Icons.Default.QuestionMark,
        "RELAXED" to Icons.Default.Spa,
        "FOCUSED" to Icons.Default.CenterFocusStrong,
        "BUSY" to Icons.Default.Work
    )
}
