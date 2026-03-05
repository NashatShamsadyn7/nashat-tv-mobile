package com.nashat.tv;

import java.io.Serializable;
import java.util.Objects;

public class Movie implements Serializable {
    private String name;
    private String poster;
    private String category;
    private String url;
    private String description;
    private String year;
    private double voteAverage;

    public Movie() {} // Needed for Firebase

    public Movie(String name, String poster, String category, String url, String description, String year) {
        this.name = name;
        this.poster = poster;
        this.category = category;
        this.url = url;
        this.description = description;
        this.year = year;
        this.voteAverage = 0.0;
    }

    public Movie(String name, String poster, String category, String url, String description, String year, double voteAverage) {
        this.name = name;
        this.poster = poster;
        this.category = category;
        this.url = url;
        this.description = description;
        this.year = year;
        this.voteAverage = voteAverage;
    }

    public String getName() { return name; }
    public String getPoster() { return poster; }
    public String getCategory() { return category; }
    public String getUrl() { return url; }
    public String getDescription() { return description; }
    public String getYear() { return year; }
    public double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(double voteAverage) { this.voteAverage = voteAverage; }
}
