package com.nashat.tv;

public class Episode {
    private String id;
    private String name;
    private String overview;
    private String image;
    private int seasonNumber;
    private int episodeNumber;

    public Episode(String id, String name, String overview, String image, int seasonNumber, int episodeNumber) {
        this.id = id;
        this.name = name;
        this.overview = overview;
        this.image = image;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOverview() { return overview; }
    public String getImage() { return image; }
    public int getSeasonNumber() { return seasonNumber; }
    public int getEpisodeNumber() { return episodeNumber; }
}
