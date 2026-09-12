package com.twinster.app.data

import com.twinster.app.domain.MusicAnalyzer
import com.twinster.app.domain.MusicProfile
import com.twinster.app.domain.ObscurityAnalyzer
import com.twinster.app.domain.ProfileArtist
import com.twinster.app.domain.ProfileSource
import com.twinster.app.domain.ProfileTrack

/**
 * Hardcoded, realistic-looking profiles so every screen (cards, sharing, Taste Twin) is fully
 * browsable without scanning a real Local Library. Runs through the same [MusicAnalyzer]
 * pipeline as real data so it stays in sync with any future scoring changes.
 */
object DemoDataProvider {

    /** Deterministic, stable placeholder art — demo data is synthetic so there's no real artwork
     *  to fetch, but a seeded picsum image still beats a blank/broken tile. */
    private fun placeholderImage(seed: String): String = "https://picsum.photos/seed/$seed/300"

    fun demoProfile(): MusicProfile = MusicAnalyzer.analyze(
        artists = mainDemoArtists,
        tracks = mainDemoTracks,
        source = ProfileSource.DEMO,
        obscurity = ObscurityAnalyzer.fromPopularity(mainDemoArtists, mainDemoTracks)
    )

    fun demoFriendProfile(): MusicProfile = MusicAnalyzer.analyze(
        artists = friendDemoArtists,
        tracks = friendDemoTracks,
        source = ProfileSource.DEMO,
        obscurity = ObscurityAnalyzer.fromPopularity(friendDemoArtists, friendDemoTracks)
    )

    const val DEMO_FRIEND_NAME = "Demo Friend"

    private val mainDemoArtists = listOf(
        ProfileArtist("d1", "Glass Animals", listOf("indie pop", "neo-psychedelic"), 78),
        ProfileArtist("d2", "Tame Impala", listOf("neo-psychedelic", "psychedelic pop"), 80),
        ProfileArtist("d3", "Men I Trust", listOf("dream pop", "bedroom pop"), 55),
        ProfileArtist("d4", "Rex Orange County", listOf("indie pop", "bedroom pop"), 70),
        ProfileArtist("d5", "Clairo", listOf("bedroom pop", "indie pop"), 68),
        ProfileArtist("d6", "Mac DeMarco", listOf("indie rock", "lo-fi"), 60),
        ProfileArtist("d7", "Beach House", listOf("dream pop", "ambient pop"), 52),
        ProfileArtist("d8", "Boy Pablo", listOf("bedroom pop", "indie pop"), 45),
        ProfileArtist("d9", "Steve Lacy", listOf("neo soul", "funk"), 74),
        ProfileArtist("d10", "Khruangbin", listOf("psychedelic soul", "funk"), 58)
    ).withPlaceholderArtistImages()

    private val mainDemoTracks = listOf(
        ProfileTrack("dt1", "Heat Waves", listOf("Glass Animals"), "2020-06-29", 85),
        ProfileTrack("dt2", "The Less I Know the Better", listOf("Tame Impala"), "2015-07-17", 82),
        ProfileTrack("dt3", "Show Me How", listOf("Men I Trust"), "2017-11-01", 60),
        ProfileTrack("dt4", "Best Friend", listOf("Rex Orange County"), "2017-04-07", 71),
        ProfileTrack("dt5", "Pretty Girl", listOf("Clairo"), "2017-08-24", 65),
        ProfileTrack("dt6", "Chamber of Reflection", listOf("Mac DeMarco"), "2014-04-15", 66),
        ProfileTrack("dt7", "Space Song", listOf("Beach House"), "2015-08-28", 58),
        ProfileTrack("dt8", "Losing You", listOf("Boy Pablo"), "2018-05-11", 48),
        ProfileTrack("dt9", "Bad Habit", listOf("Steve Lacy"), "2022-07-15", 88),
        ProfileTrack("dt10", "Time (You and I)", listOf("Khruangbin"), "2018-01-26", 55),
        ProfileTrack("dt11", "Borderline", listOf("Tame Impala"), "2020-02-14", 75),
        ProfileTrack("dt12", "Cola", listOf("Clairo"), "2017-08-02", 50)
    ).withPlaceholderTrackImages()

    private val friendDemoArtists = listOf(
        ProfileArtist("f1", "Skrillex", listOf("edm", "dubstep"), 76),
        ProfileArtist("f2", "Fred again..", listOf("edm", "dance"), 79),
        ProfileArtist("f3", "Travis Scott", listOf("hip hop", "trap"), 88),
        ProfileArtist("f4", "Kendrick Lamar", listOf("hip hop", "rap"), 90),
        ProfileArtist("f5", "Central Cee", listOf("uk drill", "rap"), 72),
        ProfileArtist("f6", "ODESZA", listOf("edm", "chillwave"), 65),
        ProfileArtist("f7", "Flume", listOf("edm", "future bass"), 68),
        ProfileArtist("f8", "Doja Cat", listOf("pop", "hip hop"), 89),
        ProfileArtist("f9", "Tyler, The Creator", listOf("hip hop", "rap"), 82),
        ProfileArtist("f10", "Peggy Gou", listOf("house", "techno"), 61)
    ).withPlaceholderArtistImages()

    private val friendDemoTracks = listOf(
        ProfileTrack("ft1", "Bangarang", listOf("Skrillex"), "2011-11-25", 79),
        ProfileTrack("ft2", "Delilah (pull me out of this)", listOf("Fred again.."), "2022-09-16", 80),
        ProfileTrack("ft3", "SICKO MODE", listOf("Travis Scott"), "2018-08-03", 91),
        ProfileTrack("ft4", "HUMBLE.", listOf("Kendrick Lamar"), "2017-03-30", 89),
        ProfileTrack("ft5", "Doja", listOf("Central Cee"), "2022-01-28", 75),
        ProfileTrack("ft6", "Say My Name", listOf("ODESZA"), "2017-06-07", 63),
        ProfileTrack("ft7", "Never Be Like You", listOf("Flume"), "2016-05-27", 70),
        ProfileTrack("ft8", "Paint The Town Red", listOf("Doja Cat"), "2023-08-04", 87),
        ProfileTrack("ft9", "EARFQUAKE", listOf("Tyler, The Creator"), "2019-05-17", 78),
        ProfileTrack("ft10", "It Makes You Forget (Itgehane)", listOf("Peggy Gou"), "2023-05-05", 60)
    ).withPlaceholderTrackImages()

    private fun List<ProfileArtist>.withPlaceholderArtistImages(): List<ProfileArtist> =
        map { it.copy(imageUrl = placeholderImage(it.id)) }

    private fun List<ProfileTrack>.withPlaceholderTrackImages(): List<ProfileTrack> =
        map { it.copy(imageUrl = placeholderImage(it.id)) }
}
