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
import com.nashat.tv.tmdb.TmdbModels.Title;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders a list of titles as poster cards, in a row or a grid. */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

    public interface OnTitleClick {
        void onTitleClick(Title title);
    }

    private final List<Title> items = new ArrayList<>();
    private final OnTitleClick listener;

    public PosterAdapter(OnTitleClick listener) {
        this.listener = listener;
        // Stable ids let RecyclerView keep bound views across a submit, so a
        // row that reloads does not flash every poster back to its placeholder.
        setHasStableIds(true);
    }

    public void submit(List<Title> titles) {
        items.clear();
        if (titles != null) items.addAll(titles);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_poster, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Title t = items.get(position);

        h.title.setText(t.displayTitle());

        String year = t.year();
        h.year.setText(year == null ? "" : year);
        h.year.setVisibility(year == null ? View.GONE : View.VISIBLE);

        // A score of 0 means "nobody has rated this", not "rated zero". Showing
        // 0.0 would read as an appalling film rather than an unknown one.
        if (t.voteAverage > 0) {
            h.ratingChip.setVisibility(View.VISIBLE);
            h.rating.setText(String.format(Locale.getDefault(), "%.1f", t.voteAverage));
        } else {
            h.ratingChip.setVisibility(View.GONE);
        }

        Glide.with(h.poster.getContext())
                .load(Tmdb.poster(t.posterPath))
                .placeholder(R.color.surface_sunken)
                .centerCrop()
                .into(h.poster);

        h.itemView.setContentDescription(t.displayTitle());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTitleClick(t);
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        // Without this an off-screen row keeps its Glide request alive; scroll
        // through a long catalogue and the pending queue grows without bound.
        Glide.with(h.poster.getContext()).clear(h.poster);
        super.onViewRecycled(h);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;
        final TextView year;
        final TextView rating;
        final View ratingChip;

        Holder(@NonNull View v) {
            super(v);
            poster = v.findViewById(R.id.poster);
            title = v.findViewById(R.id.title);
            year = v.findViewById(R.id.year);
            rating = v.findViewById(R.id.rating);
            ratingChip = v.findViewById(R.id.ratingChip);
        }
    }
}
