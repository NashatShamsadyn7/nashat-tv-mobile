package com.nashat.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nashat.tv.ContinueWatchingManager.WatchItem;
import com.nashat.tv.R;
import com.nashat.tv.player.PlaybackProgress;
import com.nashat.tv.tmdb.TmdbModels.Kind;

import java.util.ArrayList;
import java.util.List;

/** The "Continue watching" row, with a resume position and a remove control. */
public class ContinueWatchingAdapter
        extends RecyclerView.Adapter<ContinueWatchingAdapter.Holder> {

    public interface Listener {
        void onResume(WatchItem item);
        void onRemove(WatchItem item);
    }

    private final List<WatchItem> items = new ArrayList<>();
    private final Listener listener;
    private final PlaybackProgress progress;

    public ContinueWatchingAdapter(PlaybackProgress progress, Listener listener) {
        this.progress = progress;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<WatchItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /** Drops one entry without rebuilding the row, so the removal animates. */
    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public long getItemId(int position) {
        WatchItem i = items.get(position);
        // The tmdb id alone is not unique: two episodes of one series are two
        // separate entries and would otherwise collide into one stable id.
        return (i.tmdbId + ":" + i.season + ":" + i.episode).hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_continue_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        WatchItem item = items.get(position);
        h.title.setText(item.name);

        if (item.isSeries) {
            h.subtitle.setText(h.itemView.getContext()
                    .getString(R.string.player_season_episode, item.season, item.episode));
            h.subtitle.setVisibility(View.VISIBLE);
        } else if (item.year != null && !item.year.isEmpty()) {
            h.subtitle.setText(item.year);
            h.subtitle.setVisibility(View.VISIBLE);
        } else {
            h.subtitle.setVisibility(View.GONE);
        }

        // The bar only appears when a real position was recorded. A 0% bar on
        // every card would suggest nothing had been watched at all.
        int percent = resolvePercent(item);
        if (percent > 0) {
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setProgress(percent);
        } else {
            h.progress.setVisibility(View.GONE);
        }

        Glide.with(h.thumb.getContext())
                .load(item.poster)
                .placeholder(R.color.surface_sunken)
                .centerCrop()
                .into(h.thumb);

        h.itemView.setContentDescription(item.name);
        h.thumb.setOnClickListener(v -> listener.onResume(item));
        h.btnRemove.setOnClickListener(v -> listener.onRemove(item));
    }

    private int resolvePercent(WatchItem item) {
        int id;
        try {
            id = Integer.parseInt(item.tmdbId);
        } catch (NumberFormatException e) {
            return 0;
        }
        Kind kind = item.isSeries ? Kind.TV : Kind.MOVIE;
        return Math.round(progress.fraction(kind, id, item.season, item.episode) * 100f);
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        Glide.with(h.thumb.getContext()).clear(h.thumb);
        super.onViewRecycled(h);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final ImageButton btnRemove;
        final ProgressBar progress;
        final TextView title, subtitle;

        Holder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            btnRemove = v.findViewById(R.id.btnRemove);
            progress = v.findViewById(R.id.progress);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
        }
    }
}
