package com.nashat.tv;

import java.io.Serializable;

public class Actor implements Serializable {
    private String id;
    private String name;
    private String character;
    private String profilePath;

    public Actor(String id, String name, String character, String profilePath) {
        this.id = id;
        this.name = name;
        this.character = character;
        this.profilePath = profilePath;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCharacter() { return character; }
    public String getProfilePath() { return profilePath; }
}
