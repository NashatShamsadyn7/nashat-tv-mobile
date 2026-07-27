package com.nashat.tv;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class LiveTvAdapter extends RecyclerView.Adapter<LiveTvAdapter.ChannelViewHolder> {

    private final Context context;
    private final List<Channel> channels = new ArrayList<>();
    private final boolean horizontal;

    public LiveTvAdapter(Context context) {
        this(context, false);
    }

    public LiveTvAdapter(Context context, boolean horizontal) {
        this.context = context;
        this.horizontal = horizontal;
        setHasStableIds(true);
    }

    public void submitList(List<Channel> items) {
        final List<Channel> oldList = new ArrayList<>(channels);
        final List<Channel> newList = items != null ? new ArrayList<>(items) : new ArrayList<>();

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newList.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return keyOf(oldList.get(oldPos)).equals(keyOf(newList.get(newPos)));
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Channel a = oldList.get(oldPos);
                Channel b = newList.get(newPos);
                return a.getName().equals(b.getName())
                        && a.getLogo().equals(b.getLogo())
                        && a.isAlive() == b.isAlive();
            }
        }, false);

        channels.clear();
        channels.addAll(newList);
        result.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return keyOf(channels.get(position)).hashCode();
    }

    private String keyOf(Channel channel) {
        if (channel == null) return "";
        if (channel.getId() != null && !channel.getId().isEmpty()) return channel.getId();
        return channel.getName() + "|" + channel.getUrl();
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = horizontal ? R.layout.item_live_channel_horizontal : R.layout.item_live_channel;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.tvName.setText(channel.getName());
        bindLogo(holder, channel);

        // The card used to ship the category view unbound, so every channel
        // read "Category". Show the real one, and drop the line when there
        // isn't one rather than leaving an empty row.
        if (holder.tvCategory != null) {
            String category = displayCategory(channel.getCategory());
            holder.tvCategory.setText(category);
            holder.tvCategory.setVisibility(category.isEmpty() ? View.GONE : View.VISIBLE);
        }

        // The whole LIVE pill follows availability — previously only the dot
        // inside it did, leaving a badge reading "LIVE" on a dead channel.
        if (holder.liveIndicator != null) {
            holder.liveIndicator.setVisibility(channel.isAlive() ? View.VISIBLE : View.GONE);
        } else if (holder.liveDot != null) {
            holder.liveDot.setVisibility(channel.isAlive() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (!channel.hasStream()) {
                return;
            }
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("url", channel.getUrl());
            intent.putExtra("name", channel.getName());
            intent.putExtra("logo", channel.getLogo());
            intent.putExtra("category", channel.getCategory());
            context.startActivity(intent);
        });
    }

    @Override
    public void onViewRecycled(@NonNull ChannelViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.ivLogo);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    /** "Kurdish News" -> "News"; the whole line-up is Kurdish, so the prefix
     *  is noise on every single card. */
    private static String displayCategory(String category) {
        if (category == null) {
            return "";
        }
        String trimmed = category.trim();
        if (trimmed.equalsIgnoreCase("Kurdish")) {
            return "";
        }
        if (trimmed.regionMatches(true, 0, "Kurdish ", 0, 8)) {
            return trimmed.substring(8).trim();
        }
        return trimmed;
    }

    private void bindLogo(ChannelViewHolder holder, Channel channel) {
        if (!channel.hasLogo()) {
            holder.ivLogo.setImageResource(R.drawable.nashat_logo);
            return;
        }
        boolean dataSaver = SettingsActivity.isDataSaverEnabled(context);
        int targetSize = dataSaver ? 240 : 480;
        Glide.with(context)
                .load(channel.getLogo())
                .override(targetSize, targetSize)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .error(R.drawable.nashat_logo)
                .placeholder(R.drawable.nashat_logo)
                .into(holder.ivLogo);
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivLogo;
        final TextView tvName;
        final TextView tvCategory;
        final View liveDot;
        final View liveIndicator;

        ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            ivLogo = itemView.findViewById(R.id.ivChannelLogo);
            tvName = itemView.findViewById(R.id.tvChannelName);
            tvCategory = itemView.findViewById(R.id.tvChannelCategory);
            liveDot = itemView.findViewById(R.id.liveDot);
            liveIndicator = itemView.findViewById(R.id.liveIndicator);
        }
    }
}
