package com.nashat.tv;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class Channel {

    @SerializedName("name")
    private String name;

    @SerializedName("logo")
    private String logo;

    @SerializedName("category")
    private String category;

    @SerializedName("url")
    private String url;

    @SerializedName("description")
    private String description;

    public Channel() {
    }

    public Channel(String name, String logo, String category, String url, String description) {
        this.name = name;
        this.logo = logo;
        this.category = category;
        this.url = url;
        this.description = description;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public String getLogo() {
        return logo != null ? logo : "";
    }

    public String getCategory() {
        return category != null ? category : "";
    }

    public String getUrl() {
        return url != null ? url : "";
    }

    public String getDescription() {
        return description != null ? description : "";
    }
    
    // Setters added for Firebase deserialization
    public void setName(String name) { this.name = name; }
    public void setLogo(String logo) { this.logo = logo; }
    public void setCategory(String category) { this.category = category; }
    public void setUrl(String url) { this.url = url; }
    public void setDescription(String description) { this.description = description; }

    public boolean hasStream() {
        return url != null && !url.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel)) return false;
        Channel channel = (Channel) o;
        // Prefer URL as identity if present, otherwise fallback to name
        String keyThis = hasStream() ? getUrl() : getName();
        String keyOther = channel.hasStream() ? channel.getUrl() : channel.getName();
        return keyThis.equalsIgnoreCase(keyOther);
    }

    @Override
    public int hashCode() {
        String key = hasStream() ? getUrl() : getName();
        return Objects.hash(key.toLowerCase());
    }
}