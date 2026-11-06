package com.avi.peek.data.directory

enum class CreatorCategory(val label: String) {
    ALL("All"),
    SPORT("Sport"),
    MUSIC("Music"),
    FILM("Film & TV"),
    TECH("Tech"),
    NATURE("Nature & Space"),
    ART("Art & Design"),
    FASHION("Fashion & Beauty"),
    FOOD("Food"),
    GAMING("Gaming"),
    TRAVEL("Travel"),
    BUSINESS("Business"),
    COMEDY("Comedy"),
}

data class PopularCreator(
    val name: String,
    val username: String,
    val category: CreatorCategory,
    val tagline: String,
    val aliases: List<String> = emptyList(),
    val starter: Boolean = false,
)

object CreatorDirectory {

    /** Curated public accounts. Profiles are resolved live through the same guest reader as everything else. */
    val all: List<PopularCreator> = listOf(
        // Sport
        PopularCreator("Cristiano Ronaldo", "cristiano", CreatorCategory.SPORT, "Football · Portugal", listOf("ronaldo", "cr7", "football", "soccer", "portugal"), starter = true),
        PopularCreator("Leo Messi", "leomessi", CreatorCategory.SPORT, "Football · Argentina", listOf("messi", "football", "soccer", "argentina")),
        PopularCreator("Neymar Jr", "neymarjr", CreatorCategory.SPORT, "Football · Brazil", listOf("neymar", "football", "brazil")),
        PopularCreator("Virat Kohli", "virat.kohli", CreatorCategory.SPORT, "Cricket · India", listOf("cricket", "india", "kohli")),
        PopularCreator("Formula 1", "f1", CreatorCategory.SPORT, "Motorsport", listOf("formula one", "racing", "f1")),
        PopularCreator("NBA", "nba", CreatorCategory.SPORT, "Basketball", listOf("basketball", "nba")),
        PopularCreator("FIFA", "fifa", CreatorCategory.SPORT, "Football", listOf("football", "soccer", "world cup")),
        PopularCreator("Stephen Curry", "stephencurry30", CreatorCategory.SPORT, "Basketball · Golden State", listOf("curry", "basketball", "nba")),
        PopularCreator("LeBron James", "kingjames", CreatorCategory.SPORT, "Basketball · Lakers", listOf("lebron", "basketball", "nba")),
        PopularCreator("Serena Williams", "serenawilliams", CreatorCategory.SPORT, "Tennis", listOf("tennis", "serena")),
        PopularCreator("Usain Bolt", "usainbolt", CreatorCategory.SPORT, "Track & field", listOf("running", "olympics", "jamaica")),
        PopularCreator("David Beckham", "davidbeckham", CreatorCategory.SPORT, "Football · England", listOf("beckham", "football", "soccer")),
        PopularCreator("Roger Federer", "rogerfederer", CreatorCategory.SPORT, "Tennis", listOf("tennis", "federer")),
        PopularCreator("Lewis Hamilton", "lewishamilton", CreatorCategory.SPORT, "Formula 1", listOf("f1", "racing", "mercedes")),
        PopularCreator("Mike Tyson", "miketyson", CreatorCategory.SPORT, "Boxing", listOf("boxing", "tyson")),

        // Music
        PopularCreator("Taylor Swift", "taylorswift", CreatorCategory.MUSIC, "Singer-songwriter", listOf("taylor", "swift", "pop", "music"), starter = true),
        PopularCreator("Billie Eilish", "billieeilish", CreatorCategory.MUSIC, "Singer-songwriter", listOf("billie", "eilish", "pop", "music")),
        PopularCreator("Beyoncé", "beyonce", CreatorCategory.MUSIC, "Artist", listOf("beyonce", "music", "r&b")),
        PopularCreator("Rihanna", "badgalriri", CreatorCategory.MUSIC, "Artist · Fenty", listOf("rihanna", "music", "fenty")),
        PopularCreator("Dua Lipa", "dualipa", CreatorCategory.MUSIC, "Pop artist", listOf("dua", "lipa", "pop", "music")),
        PopularCreator("The Weeknd", "theweeknd", CreatorCategory.MUSIC, "Artist", listOf("weeknd", "abel", "music")),
        PopularCreator("Ariana Grande", "arianagrande", CreatorCategory.MUSIC, "Pop artist", listOf("ariana", "grande", "pop", "music")),
        PopularCreator("Shakira", "shakira", CreatorCategory.MUSIC, "Artist", listOf("shakira", "music", "latin")),
        PopularCreator("Olivia Rodrigo", "oliviarodrigo", CreatorCategory.MUSIC, "Singer-songwriter", listOf("olivia", "rodrigo", "pop", "music")),
        PopularCreator("SZA", "sza", CreatorCategory.MUSIC, "Artist", listOf("sza", "music", "r&b")),
        PopularCreator("Lady Gaga", "ladygaga", CreatorCategory.MUSIC, "Artist", listOf("gaga", "pop", "music")),
        PopularCreator("Post Malone", "postmalone", CreatorCategory.MUSIC, "Artist", listOf("post", "malone", "music")),
        PopularCreator("Bruno Mars", "brunomars", CreatorCategory.MUSIC, "Artist", listOf("bruno", "mars", "music")),
        PopularCreator("Spotify", "spotify", CreatorCategory.MUSIC, "Music streaming", listOf("music", "podcasts", "spotify")),

        // Film & TV
        PopularCreator("Netflix", "netflix", CreatorCategory.FILM, "Film & series", listOf("movies", "series", "streaming"), starter = true),
        PopularCreator("Disney", "disney", CreatorCategory.FILM, "Stories & magic", listOf("movies", "disney")),
        PopularCreator("Marvel", "marvel", CreatorCategory.FILM, "Super heroes", listOf("movies", "comics", "marvel")),
        PopularCreator("Warner Bros.", "warnerbros", CreatorCategory.FILM, "Film studio", listOf("movies", "warner")),
        PopularCreator("HBO", "hbo", CreatorCategory.FILM, "Series & film", listOf("tv", "series", "hbo")),
        PopularCreator("Prime Video", "primevideo", CreatorCategory.FILM, "Film & series", listOf("movies", "amazon", "streaming")),
        PopularCreator("Paramount+", "paramountplus", CreatorCategory.FILM, "Streaming", listOf("movies", "series", "streaming")),
        PopularCreator("Pixar", "pixar", CreatorCategory.FILM, "Animation", listOf("animation", "movies", "pixar")),
        PopularCreator("Zendaya", "zendaya", CreatorCategory.FILM, "Actor", listOf("zendaya", "actor", "euphoria")),
        PopularCreator("Dwayne Johnson", "therock", CreatorCategory.FILM, "Actor & producer", listOf("the rock", "dwayne", "actor", "wrestling")),
        PopularCreator("Saturday Night Live", "nbcsnl", CreatorCategory.FILM, "Comedy show", listOf("snl", "comedy", "tv")),
        PopularCreator("Jimmy Fallon", "jimmyfallon", CreatorCategory.FILM, "Talk show host", listOf("tonight show", "comedy", "tv")),

        // Tech
        PopularCreator("Google", "google", CreatorCategory.TECH, "Technology", listOf("tech", "search", "android"), starter = true),
        PopularCreator("Apple", "apple", CreatorCategory.TECH, "Technology", listOf("iphone", "mac", "tech")),
        PopularCreator("Microsoft", "microsoft", CreatorCategory.TECH, "Technology", listOf("windows", "tech")),
        PopularCreator("SpaceX", "spacex", CreatorCategory.TECH, "Space technology", listOf("space", "rockets", "elon")),
        PopularCreator("OpenAI", "openai", CreatorCategory.TECH, "Artificial intelligence", listOf("ai", "chatgpt", "technology")),
        PopularCreator("Meta", "meta", CreatorCategory.TECH, "Technology", listOf("facebook", "instagram", "tech")),
        PopularCreator("NVIDIA", "nvidia", CreatorCategory.TECH, "Computing & AI", listOf("gpu", "ai", "tech")),
        PopularCreator("Samsung", "samsung", CreatorCategory.TECH, "Technology", listOf("phones", "tech")),
        PopularCreator("Adobe", "adobe", CreatorCategory.TECH, "Creative software", listOf("photoshop", "design", "software")),
        PopularCreator("Canva", "canva", CreatorCategory.TECH, "Design tools", listOf("design", "software")),

        // Nature & Space
        PopularCreator("NASA", "nasa", CreatorCategory.NATURE, "Space & exploration", listOf("space", "rockets", "science"), starter = true),
        PopularCreator("NASA Webb Telescope", "nasawebb", CreatorCategory.NATURE, "Deep space", listOf("space", "telescope", "science")),
        PopularCreator("National Geographic", "natgeo", CreatorCategory.NATURE, "Planet & exploration", listOf("nature", "travel", "science", "photography"), starter = true),
        PopularCreator("BBC Earth", "bbcearth", CreatorCategory.NATURE, "Wildlife & planet", listOf("nature", "animals", "wildlife")),
        PopularCreator("WWF International", "wwf", CreatorCategory.NATURE, "Wildlife conservation", listOf("nature", "animals", "conservation")),
        PopularCreator("Discovery", "discovery", CreatorCategory.NATURE, "Adventure & nature", listOf("nature", "adventure", "tv")),
        PopularCreator("Nature", "nature", CreatorCategory.NATURE, "Science journal", listOf("science", "research", "nature")),
        PopularCreator("CERN", "cern", CreatorCategory.NATURE, "Particle physics", listOf("science", "physics", "space")),
        PopularCreator("Science Magazine", "sciencemagazine", CreatorCategory.NATURE, "Science news", listOf("science", "research")),
        PopularCreator("National Geographic Travel", "natgeotravel", CreatorCategory.TRAVEL, "Travel & places", listOf("travel", "places", "nature")),

        // Art & design
        PopularCreator("Musée du Louvre", "museelouvre", CreatorCategory.ART, "Art museum · Paris", listOf("louvre", "art", "museum", "paris")),
        PopularCreator("MoMA", "themuseumofmodernart", CreatorCategory.ART, "Modern art · New York", listOf("moma", "art", "museum", "new york")),
        PopularCreator("Tate", "tate", CreatorCategory.ART, "Art galleries · UK", listOf("art", "museum", "london")),
        PopularCreator("British Museum", "britishmuseum", CreatorCategory.ART, "History & art · London", listOf("museum", "history", "art")),
        PopularCreator("The Met", "metmuseum", CreatorCategory.ART, "Art museum · New York", listOf("met", "art", "museum")),
        PopularCreator("National Gallery", "nationalgallery", CreatorCategory.ART, "Paintings · London", listOf("art", "gallery", "london")),
        PopularCreator("ArchDaily", "archdaily", CreatorCategory.ART, "Architecture", listOf("architecture", "design")),
        PopularCreator("Dezeen", "dezeen", CreatorCategory.ART, "Architecture & design", listOf("architecture", "design")),
        PopularCreator("Magnum Photos", "magnumphotos", CreatorCategory.ART, "Photography", listOf("photography", "photojournalism")),
        PopularCreator("V&A", "vamuseum", CreatorCategory.ART, "Art & design · London", listOf("museum", "design", "art")),

        // Fashion & beauty
        PopularCreator("Vogue", "voguemagazine", CreatorCategory.FASHION, "Fashion & culture", listOf("fashion", "style", "magazine"), starter = true),
        PopularCreator("Gucci", "gucci", CreatorCategory.FASHION, "Fashion house", listOf("fashion", "luxury", "style")),
        PopularCreator("Nike", "nike", CreatorCategory.FASHION, "Sportswear", listOf("sportswear", "shoes", "fitness")),
        PopularCreator("adidas", "adidas", CreatorCategory.FASHION, "Sportswear", listOf("sportswear", "shoes", "fitness")),
        PopularCreator("Sephora", "sephora", CreatorCategory.FASHION, "Beauty", listOf("beauty", "makeup", "skincare")),
        PopularCreator("Fenty Beauty", "fentybeauty", CreatorCategory.FASHION, "Beauty by Rihanna", listOf("beauty", "makeup", "rihanna")),
        PopularCreator("Kylie Cosmetics", "kyliecosmetics", CreatorCategory.FASHION, "Beauty", listOf("beauty", "makeup", "kylie")),
        PopularCreator("Rare Beauty", "rarebeauty", CreatorCategory.FASHION, "Beauty by Selena Gomez", listOf("beauty", "makeup", "selena")),
        PopularCreator("MAC Cosmetics", "maccosmetics", CreatorCategory.FASHION, "Makeup", listOf("beauty", "makeup")),
        PopularCreator("Charlotte Tilbury", "charlottetilbury", CreatorCategory.FASHION, "Beauty", listOf("beauty", "makeup")),

        // Food
        PopularCreator("Gordon Ramsay", "gordongram", CreatorCategory.FOOD, "Chef & restaurants", listOf("chef", "cooking", "food", "ramsay"), starter = true),
        PopularCreator("Jamie Oliver", "jamieoliver", CreatorCategory.FOOD, "Chef & recipes", listOf("chef", "cooking", "food", "recipes")),
        PopularCreator("Tasty", "buzzfeedtasty", CreatorCategory.FOOD, "Recipes", listOf("recipes", "cooking", "food")),
        PopularCreator("Food Network", "foodnetwork", CreatorCategory.FOOD, "Food & cooking", listOf("recipes", "cooking", "food")),
        PopularCreator("Bon Appétit", "bonappetitmag", CreatorCategory.FOOD, "Food magazine", listOf("recipes", "food", "cooking")),
        PopularCreator("Nigella", "nigellalawson", CreatorCategory.FOOD, "Chef & author", listOf("chef", "recipes", "food")),
        PopularCreator("Food & Wine", "foodandwine", CreatorCategory.FOOD, "Food & drink", listOf("recipes", "food", "wine")),

        // Gaming
        PopularCreator("PlayStation", "playstation", CreatorCategory.GAMING, "Games", listOf("gaming", "games", "playstation")),
        PopularCreator("Xbox", "xbox", CreatorCategory.GAMING, "Games", listOf("gaming", "games", "xbox")),
        PopularCreator("Rockstar Games", "rockstargames", CreatorCategory.GAMING, "Games", listOf("gaming", "gta", "games")),
        PopularCreator("Epic Games", "epicgames", CreatorCategory.GAMING, "Games & Fortnite", listOf("gaming", "fortnite", "games")),
        PopularCreator("Riot Games", "riotgames", CreatorCategory.GAMING, "Games", listOf("gaming", "league of legends", "valorant")),
        PopularCreator("Minecraft", "minecraft", CreatorCategory.GAMING, "Games", listOf("gaming", "minecraft", "games")),
        PopularCreator("Fortnite", "fortnite", CreatorCategory.GAMING, "Games", listOf("gaming", "fortnite")),
        PopularCreator("EA", "ea", CreatorCategory.GAMING, "Games", listOf("gaming", "games", "ea")),
        PopularCreator("Ubisoft", "ubisoft", CreatorCategory.GAMING, "Games", listOf("gaming", "games")),
        PopularCreator("Crunchyroll", "crunchyroll", CreatorCategory.GAMING, "Anime & games", listOf("anime", "manga", "tv")),

        // Travel
        PopularCreator("Lonely Planet", "lonelyplanet", CreatorCategory.TRAVEL, "Travel guides", listOf("travel", "places", "guides")),
        PopularCreator("Discover Earth", "discoverearth", CreatorCategory.TRAVEL, "Earth & travel", listOf("travel", "nature", "places")),
        PopularCreator("Beautiful Destinations", "beautifuldestinations", CreatorCategory.TRAVEL, "Travel films", listOf("travel", "places", "videos")),
        PopularCreator("Condé Nast Traveler", "cntraveler", CreatorCategory.TRAVEL, "Travel magazine", listOf("travel", "places", "magazine")),
        PopularCreator("Travel + Leisure", "travelandleisure", CreatorCategory.TRAVEL, "Travel magazine", listOf("travel", "places", "magazine")),

        // Business
        PopularCreator("Forbes", "forbes", CreatorCategory.BUSINESS, "Business & leadership", listOf("business", "money", "news")),
        PopularCreator("Business Insider", "businessinsider", CreatorCategory.BUSINESS, "Business news", listOf("business", "news", "tech")),
        PopularCreator("Bloomberg", "bloomberg", CreatorCategory.BUSINESS, "Business & markets", listOf("business", "finance", "news")),
        PopularCreator("CNBC", "cnbc", CreatorCategory.BUSINESS, "Business news", listOf("business", "finance", "markets")),
        PopularCreator("The Wall Street Journal", "wsj", CreatorCategory.BUSINESS, "Business news", listOf("business", "news", "finance")),
        PopularCreator("Fast Company", "fastcompany", CreatorCategory.BUSINESS, "Business & design", listOf("business", "design", "innovation")),

        // Comedy
        PopularCreator("9GAG", "9gag", CreatorCategory.COMEDY, "Memes & fun", listOf("memes", "comedy", "funny")),
        PopularCreator("BuzzFeed", "buzzfeed", CreatorCategory.COMEDY, "Pop culture & fun", listOf("comedy", "quizzes", "fun")),
        PopularCreator("Kevin Hart", "kevinhart4real", CreatorCategory.COMEDY, "Comedian", listOf("comedy", "stand up", "actor")),
    )

    val starter: List<PopularCreator> = all.filter { it.starter }

    val trending: List<PopularCreator> = listOfNotNull(
        byUsername("cristiano"),
        byUsername("taylorswift"),
        byUsername("therock"),
        byUsername("zendaya"),
        byUsername("natgeo"),
        byUsername("nasa"),
        byUsername("netflix"),
        byUsername("google"),
        byUsername("voguemagazine"),
        byUsername("gordongram"),
        byUsername("billieeilish"),
        byUsername("f1"),
    )

    val suggestedSearches: List<String> = listOf(
        "Cristiano Ronaldo",
        "Taylor Swift",
        "NASA",
        "Zendaya",
        "Netflix",
        "Formula 1",
        "National Geographic",
        "Gordon Ramsay",
    )

    fun byUsername(username: String): PopularCreator? =
        all.firstOrNull { it.username.equals(username, ignoreCase = true) }

    fun forCategory(category: CreatorCategory): List<PopularCreator> {
        if (category == CreatorCategory.ALL) {
            val ids = trending.map { it.username }.toSet()
            return trending + all.filter { it.username !in ids }
        }
        return all.filter { it.category == category }
    }

    fun search(query: String, limit: Int = 12): List<PopularCreator> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val tokens = normalized.split(Regex("\\s+")).filter { it.length > 0 }
        if (tokens.isEmpty()) return emptyList()

        return all.mapNotNull { creator ->
            val haystack = buildString {
                append(creator.name.lowercase()).append(' ')
                append(creator.username.lowercase()).append(' ')
                append(creator.tagline.lowercase()).append(' ')
                append(creator.category.label.lowercase()).append(' ')
                creator.aliases.forEach { append(it.lowercase()).append(' ') }
            }
            if (!tokens.all { haystack.contains(it) }) return@mapNotNull null
            val score = when {
                creator.name.equals(query, ignoreCase = true) -> 0
                creator.username.equals(normalized.removePrefix("@"), ignoreCase = true) -> 0
                creator.name.lowercase().startsWith(normalized) -> 1
                creator.username.lowercase().startsWith(normalized.removePrefix("@")) -> 1
                else -> 2
            }
            score to creator
        }.sortedWith(compareBy<Pair<Int, PopularCreator>> { it.first }.thenByDescending { it.second.starter })
            .map { it.second }
            .take(limit)
    }

    fun categoryCreators(category: CreatorCategory): List<PopularCreator> {
        return if (category == CreatorCategory.ALL) trending else all.filter { it.category == category }
    }

    val creatorCategories: List<CreatorCategory> = CreatorCategory.entries.filter { it != CreatorCategory.ALL }
}
