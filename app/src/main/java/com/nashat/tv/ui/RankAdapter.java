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

/**
 * The Top 10 row: each poster stands on its rank, drawn as a large outline
 * numeral behind it. The order is the information, so it is shown rather than
 * left for the viewer to count.
 */
public class RankAdapter extends RecyclerView.Adapter<RankAdapter.Holder> {

    private static final int LIMIT = 10;

    private final List<Title> items = new ArrayList<>();
    private final PosterAdapter.OnTitleClick listener;

    public RankAdapter(PosterAdapter.OnTitleClick listener) {
        this.listener = listener;
    }

    public void submit(List<Title> titles) {
        items.clear();
        if (titles != null) {
            items.addAll(titles.subList(0, Math.min(LIMIT, titles.size())));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rank_poster, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Title t = items.get(position);
        int rank = position + 1;
        h.rank.setText(String.format(Locale.US, "%d", rank));

        // Two digits need more room before the poster starts.
        float density = h.itemView.getResources().getDisplayMetrics().density;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.poster.getLayoutParams();
        lp.setMarginStart(Math.round((rank >= 10 ? 76 : 44) * density));
        h.poster.setLayoutParams(lp);

        Glide.with(h.poster.getContext())
                .load(Tmdb.poster(t.posterPath))
                .placeholder(R.color.surface_sunken)
                .centerCrop()
                .into(h.poster);

        h.itemView.setContentDescription(rank + ". " + t.displayTitle());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTitleClick(t);
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        Glide.with(h.poster.getContext()).clear(h.poster);
        super.onViewRecycled(h);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank;
        final ImageView poster;

        Holder(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.rank);
            poster = v.findViewById(R.id.poster);
            // Outline, not fill: a solid numeral that size would outweigh the poster.
            rank.getPaint().setStyle(android.graphics.Paint.Style.STROKE);
            rank.getPaint().setStrokeWidth(2.5f * v.getResources().getDisplayMetrics().density);
        }
    }
}
