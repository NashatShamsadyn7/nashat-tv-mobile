package com.nashat.tv;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class Channel {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("logo")
    private String logo;

    @SerializedName("logoUrl")
    private String logoUrl;

    @SerializedName("category")
    private String category;

    @SerializedName("url")
    private String url;

    @SerializedName("streamUrl")
    private String streamUrl;

    @SerializedName("description")
    private String description;

    @SerializedName("country")
    private String country;

    @SerializedName("language")
    private String language;

    @SerializedName("ping")
    private int ping;

    @SerializedName("loadSpeed")
    private int loadSpeed;

    @SerializedName("isAlive")
    private boolean isAlive = true;

    @SerializedName("lastChecked")
    private String lastChecked;

    @SerializedName("source")
    private String source;

    @SerializedName("dbIndex")
    private int dbIndex = -1;

    public Channel() {
    }

    public int getDbIndex() {
        return dbIndex;
    }

    public void setDbIndex(int dbIndex) {
        this.dbIndex = dbIndex;
    }

    public Channel(String name, String logo, String category, String url, String description) {
        this.name = name;
        this.logo = logo;
        this.logoUrl = logo;
        this.category = category;
        this.url = url;
        this.streamUrl = url;
        this.description = description;
    }

    // Getters
    public String getId() { return id != null ? id : ""; }
    public String getName() { return name != null ? name : ""; }
    public String getLogo() {
        if (logoUrl != null && !logoUrl.trim().isEmpty()) return logoUrl;
        return logo != null ? logo : "";
    }
    public String getCategory() { return category != null ? category : ""; }
    public String getUrl() {
        if (streamUrl != null && !streamUrl.trim().isEmpty()) return streamUrl;
        return url != null ? url : "";
    }
    public String getDescription() { return description != null ? description : ""; }
    public String getCountry() { return country != null ? country : ""; }
    public String getLanguage() { return language != null ? language : ""; }
    public int getPing() { return ping; }
    public int getLoadSpeed() { return loadSpeed; }
    public boolean isAlive() { return isAlive; }
    public String getLastChecked() { return lastChecked != null ? lastChecked : ""; }
    public String getSource() { return source != null ? source : ""; }

    // Setters for Firebase deserialization
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setLogo(String logo) {
        this.logo = logo;
        if (this.logoUrl == null || this.logoUrl.trim().isEmpty()) {
            this.logoUrl = logo;
        }
    }
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
        if (this.logo == null || this.logo.trim().isEmpty()) {
            this.logo = logoUrl;
        }
    }
    public void setCategory(String category) { this.category = category; }
    public void setUrl(String url) {
        this.url = url;
        if (this.streamUrl == null || this.streamUrl.trim().isEmpty()) {
            this.streamUrl = url;
        }
    }
    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
        if (this.url == null || this.url.trim().isEmpty()) {
            this.url = streamUrl;
        }
    }
    public void setDescription(String description) { this.description = description; }
    public void setCountry(String country) { this.country = country; }
    public void setLanguage(String language) { this.language = language; }
    public void setPing(int ping) { this.ping = ping; }
    public void setLoadSpeed(int loadSpeed) { this.loadSpeed = loadSpeed; }
    public void setAlive(boolean alive) { this.isAlive = alive; }
    public void setLastChecked(String lastChecked) { this.lastChecked = lastChecked; }
    public void setSource(String source) { this.source = source; }

    public boolean hasStream() {
        return getUrl() != null && !getUrl().trim().isEmpty();
    }

    public boolean hasLogo() {
        return getLogo() != null && !getLogo().trim().isEmpty();
    }

    public int getHealthScore() {
        int pingScore = ping > 0 ? Math.max(0, 500 - ping) : 0;
        int loadScore = loadSpeed > 0 ? Math.max(0, 5000 - loadSpeed) : 0;
        return pingScore + loadScore + (isAlive ? 1000 : 0);
    }

    public String getMetricLabel() {
        StringBuilder builder = new StringBuilder();
        if (ping > 0) {
            builder.append(ping).append(" ms");
        }
        if (loadSpeed > 0) {
            if (builder.length() > 0) builder.append("  •  ");
            builder.append(loadSpeed).append(" ms open");
        }
        if (builder.length() == 0) {
            builder.append(isAlive ? "Online" : "Offline");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel)) return false;
        Channel channel = (Channel) o;
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
