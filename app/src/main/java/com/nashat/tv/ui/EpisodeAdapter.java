package com.nashat.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nashat.tv.R;
import com.nashat.tv.tmdb.Tmdb;
import com.nashat.tv.tmdb.TmdbModels.Episode;

import java.util.ArrayList;
import java.util.List;

/** The episode list of one season. */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {

    public interface OnEpisodeClick {
        void onEpisodeClick(Episode episode);
    }

    private final List<Episode> items = new ArrayList<>();
    private final OnEpisodeClick listener;

    public EpisodeAdapter(OnEpisodeClick listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Episode> episodes) {
        items.clear();
        if (episodes != null) items.addAll(episodes);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Episode e = items.get(position);
        String label = h.itemView.getContext()
                .getString(R.string.details_episode_number, e.episodeNumber);

        String name = e.name == null || e.name.isEmpty() ? label : label + " · " + e.name;
        h.title.setText(name);

        // Air date is the only metadata here; when TMDB has none, the line is
        // removed rather than left as an empty gap between title and synopsis.
        if (e.airDate != null && !e.airDate.isEmpty()) {
            h.meta.setText(e.airDate);
            h.meta.setVisibility(View.VISIBLE);
        } else {
            h.meta.setVisibility(View.GONE);
        }

        if (e.overview != null && !e.overview.isEmpty()) {
            h.overview.setText(e.overview);
            h.overview.setVisibility(View.VISIBLE);
        } else {
            h.overview.setVisibility(View.GONE);
        }

        Glide.with(h.still.getContext())
                .load(Tmdb.still(e.stillPath))
                .placeholder(R.color.surface_sunken)
                .centerCrop()
                .into(h.still);

        h.itemView.setContentDescription(name);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEpisodeClick(e);
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        Glide.with(h.still.getContext()).clear(h.still);
        super.onViewRecycled(h);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView still;
        final TextView title, meta, overview;

        Holder(@NonNull View v) {
            super(v);
            still = v.findViewById(R.id.still);
            title = v.findViewById(R.id.episodeTitle);
            meta = v.findViewById(R.id.episodeMeta);
            overview = v.findViewById(R.id.episodeOverview);
        }
    }
}
