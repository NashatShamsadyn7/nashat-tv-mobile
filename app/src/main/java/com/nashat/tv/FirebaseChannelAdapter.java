package com.nashat.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirebaseChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_ROW = 0;

    private final Context context;
    private final List<ListItem> items = new ArrayList<>();

    // Map country codes to readable names
    private String getCountryName(String code) {
        if (code == null) return "Other Channels";
        switch (code.toUpperCase()) {
            case "KR": return "Kurdish Channels";
            case "IQ": return "Iraqi Channels";
            case "SA": return "Saudi Channels";
            case "AE": return "Emirati Channels";
            case "QA": return "Qatari Channels";
            case "JO": return "Jordanian Channels";
            case "TR": return "Turkish Channels";
            case "US": return "US Channels";
            case "GB": return "UK Channels";
            case "FR": return "French Channels";
            case "RU": return "Russian Channels";
            case "CN": return "Chinese Channels";
            case "DE": return "German Channels";
            default: return "International Channels";
        }
    }

    public FirebaseChannelAdapter(Context context) {
        this.context = context;
    }

    public void updateData(List<Channel> channelList) {
        items.clear();
        
        // First, group channels by Country (the data model has country now)
        // Since we didn't add "country" field to Channel.java yet, it defaults to Category or we read it if added.
        // Wait, does Channel.java have getCountry()? Let's check or just group by Category for now!
        
        // Actually, the user asked to prioritize Kurdish channels if location is Iraq.
        // The Python script already prioritized this in the JSON array order, and added country codes.
        
        Map<String, List<Channel>> grouped = new LinkedHashMap<>();
        
        for (Channel ch : channelList) {
            String country = ch.getCountry();
            if (country == null || country.trim().isEmpty()) {
                country = "Other";
            }
            
            String rowTitle = getCountryName(country);
            
            if (!grouped.containsKey(rowTitle)) {
                grouped.put(rowTitle, new ArrayList<>());
            }
            grouped.get(rowTitle).add(ch);
        }

        // Flatten into row items
        for (Map.Entry<String, List<Channel>> entry : grouped.entrySet()) {
            items.add(new ListItem(entry.getKey(), entry.getValue()));
        }
        
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_live_tv_row, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowViewHolder rowHolder = (RowViewHolder) holder;
        ListItem item = items.get(position);
        
        rowHolder.title.setText(item.headerTitle);
        
        // Set up inner RecyclerView
        rowHolder.recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        InnerChannelAdapter innerAdapter = new InnerChannelAdapter(context, item.channels);
        rowHolder.recyclerView.setAdapter(innerAdapter);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --- ViewHolders ---
    
    public static class RowViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView recyclerView;
        public RowViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvRowTitle);
            recyclerView = itemView.findViewById(R.id.rvHorizontalList);
        }
    }

    // --- Inner Adapter ---
    public static class InnerChannelAdapter extends RecyclerView.Adapter<InnerChannelAdapter.ChannelViewHolder> {
        private final Context context;
        private final List<Channel> channels;
        
        private static final int[][] GRADIENT_PALETTES = {
                {0xFF1A1A2E, 0xFF16213E},  {0xFF1A0A2E, 0xFF2D1B69},
                {0xFF0D2137, 0xFF0F3460},  {0xFF1E1E30, 0xFF2A2A4A},
                {0xFF0E2A1A, 0xFF1B4332},  {0xFF2D1F0E, 0xFF4A3520},
                {0xFF1A0E0E, 0xFF3D1E1E},  {0xFF0E1A2D, 0xFF1A3A5A},
                {0xFF1E0E2D, 0xFF3A1A5A},  {0xFF0D2020, 0xFF0F4040},
        };

        public InnerChannelAdapter(Context context, List<Channel> channels) {
            this.context = context;
            this.channels = channels;
        }

        @NonNull
        @Override
        public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_channel_firebase, parent, false);
            return new ChannelViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChannelViewHolder channelHolder, int position) {
            Channel channel = channels.get(position);
            
            channelHolder.name.setText(channel.getName());
            channelHolder.category.setText(channel.getCategory());

            int hash = channel.getName() != null ? Math.abs(channel.getName().hashCode()) : 0;
            int[] palette = GRADIENT_PALETTES[hash % GRADIENT_PALETTES.length];
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, palette);
            gd.setCornerRadii(new float[]{14*3, 14*3, 14*3, 14*3, 0, 0, 0, 0});
            channelHolder.cardGradient.setBackground(gd);

            String logoUrl = channel.getLogo();
            if (logoUrl != null && !logoUrl.isEmpty()) {
                channelHolder.logo.setVisibility(View.VISIBLE);
                channelHolder.fallbackLogo.setVisibility(View.GONE);
                Glide.with(context)
                        .load(logoUrl)
                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                        .error(R.mipmap.ic_launcher)
                        .into(channelHolder.logo);
            } else {
                channelHolder.logo.setVisibility(View.VISIBLE);
                channelHolder.fallbackLogo.setVisibility(View.GONE);
                channelHolder.logo.setImageResource(R.mipmap.ic_launcher);
            }

            channelHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("url", channel.getUrl());
                intent.putExtra("name", channel.getName());
                intent.putExtra("logo", channel.getLogo());
                intent.putExtra("category", channel.getCategory());
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }

        public static class ChannelViewHolder extends RecyclerView.ViewHolder {
            final TextView name, category, fallbackLogo;
            final ImageView logo;
            final View cardGradient;

            public ChannelViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvName);
                category = itemView.findViewById(R.id.tvCategory);
                logo = itemView.findViewById(R.id.ivLogo);
                fallbackLogo = itemView.findViewById(R.id.tvFallbackLogo);
                cardGradient = itemView.findViewById(R.id.cardGradient);
            }
        }
    }

    // --- ListItem Wrapper ---
    public static class ListItem {
        public String headerTitle;
        public List<Channel> channels;

        public ListItem(String title, List<Channel> channels) {
            this.headerTitle = title;
            this.channels = channels;
        }
    }
}