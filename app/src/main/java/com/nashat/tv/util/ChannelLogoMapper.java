package com.nashat.tv.util;

import java.util.HashMap;
import java.util.Map;

public class ChannelLogoMapper {
    private static final Map<String, String> LOGOS = new HashMap<>();

    static {
        // Known Kurdish and regional channels
        LOGOS.put("rudaw", "https://upload.wikimedia.org/wikipedia/en/thumb/e/e0/Rudaw_Media_Network_Logo.svg/1200px-Rudaw_Media_Network_Logo.svg.png");
        LOGOS.put("kurdistan 24", "https://k24.fra1.cdn.digitaloceanspaces.com/logo/K24-Logo.png");
        LOGOS.put("k24", "https://k24.fra1.cdn.digitaloceanspaces.com/logo/K24-Logo.png");
        LOGOS.put("kurdsat", "https://m.media-amazon.com/images/I/41D0vA31zNL.png");
        LOGOS.put("kurdsat news", "https://upload.wikimedia.org/wikipedia/commons/4/43/Kurdsat_News.png");
        LOGOS.put("zagros", "https://upload.wikimedia.org/wikipedia/en/9/91/Zagros_TV_logo.png");
        LOGOS.put("zagros tv", "https://upload.wikimedia.org/wikipedia/en/9/91/Zagros_TV_logo.png");
        LOGOS.put("ava", "https://play-lh.googleusercontent.com/x0Hw4010qjXofUa_dOfvK1gq45h_55v_c1lR_vX3D-T2_R15Tz3S2W9pW7fWeSGYaA");
        LOGOS.put("ava entertainment", "https://play-lh.googleusercontent.com/x0Hw4010qjXofUa_dOfvK1gq45h_55v_c1lR_vX3D-T2_R15Tz3S2W9pW7fWeSGYaA");
        LOGOS.put("waartv", "https://waartv.net/assets/images/logo.png");
        LOGOS.put("waar tv", "https://waartv.net/assets/images/logo.png");
        LOGOS.put("speda", "https://play-lh.googleusercontent.com/j1_hJ1R4wS308I8vU-C-5rY_r1kG9jV-52aA_pXXK0K_2Uu9rQYqS0k_j2h-Qk_Qf0A");
        LOGOS.put("payam", "https://telegramchannels.me/storage/media/logo/849/f61f77d3a0/payamtv_en.jpg");
        LOGOS.put("nrt", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/NRT_logo.svg/1200px-NRT_logo.svg.png");
        LOGOS.put("nrt 1", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/NRT_logo.svg/1200px-NRT_logo.svg.png");
        LOGOS.put("nrt 2", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/NRT_logo.svg/1200px-NRT_logo.svg.png");
        LOGOS.put("kurdmax", "https://upload.wikimedia.org/wikipedia/en/thumb/0/05/Kurdmax_logo.png/250px-Kurdmax_logo.png");
        LOGOS.put("kurdmax show", "https://i.imgur.com/XTmyGmL.png");
        LOGOS.put("kurdmax pepule", "https://i.imgur.com/JkK8HG8.png");
        LOGOS.put("kurdmax sorani", "https://i.imgur.com/JkK8HG8.png");
        LOGOS.put("kurdmax kurmanci", "https://i.imgur.com/XTmyGmL.png");
        LOGOS.put("kurdmax badini", "https://i.imgur.com/XTmyGmL.png");
        LOGOS.put("kurdmax music", "https://i.imgur.com/otnIdEu.png");
        LOGOS.put("gali kurdistan", "https://upload.wikimedia.org/wikipedia/en/f/f7/Gali_Kurdistan_logo.png");
        LOGOS.put("ronahi", "https://upload.wikimedia.org/wikipedia/commons/1/1a/Ronahi_TV.png");
        LOGOS.put("sterk", "https://upload.wikimedia.org/wikipedia/commons/2/23/Sterk_tv_logo.png");
        LOGOS.put("zarok", "https://upload.wikimedia.org/wikipedia/en/8/87/Zarok_TV_Logo.png");
        LOGOS.put("channel 8", "https://channel8.com/logo.png"); // placeholder
    }

    public static String getLogoForChannel(String channelName) {
        if (channelName == null) return "";
        String normalized = channelName.toLowerCase().replaceAll("[^a-z0-9]", " ");
        for (Map.Entry<String, String> entry : LOGOS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }
}
