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
import com.nashat.tv.tmdb.TmdbModels.CastMember;

import java.util.ArrayList;
import java.util.List;

/** The cast row on the details screen. */
public class CastAdapter extends RecyclerView.Adapter<CastAdapter.Holder> {

    public interface OnCastClick {
        void onCastClick(CastMember member);
    }

    /** Beyond this the row is extras and background artists nobody scrolls to. */
    private static final int MAX_SHOWN = 30;

    private final List<CastMember> items = new ArrayList<>();
    private final OnCastClick listener;

    public CastAdapter(OnCastClick listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<CastMember> cast) {
        items.clear();
        if (cast != null) {
            for (CastMember m : cast) {
                if (items.size() >= MAX_SHOWN) break;
                if (m != null) items.add(m);
            }
        }
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
                .inflate(R.layout.item_cast_member, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CastMember m = items.get(position);

        h.name.setText(m.name == null ? "" : m.name);
        if (m.character != null && !m.character.isEmpty()) {
            h.character.setText(m.character);
            h.character.setVisibility(View.VISIBLE);
        } else {
            h.character.setVisibility(View.GONE);
        }

        Glide.with(h.profile.getContext())
                .load(Tmdb.profile(m.profilePath))
                .placeholder(R.color.surface_sunken)
                .centerCrop()
                .into(h.profile);

        h.itemView.setContentDescription(m.name);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCastClick(m);
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        Glide.with(h.profile.getContext()).clear(h.profile);
        super.onViewRecycled(h);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView profile;
        final TextView name, character;

        Holder(@NonNull View v) {
            super(v);
            profile = v.findViewById(R.id.profile);
            name = v.findViewById(R.id.actorName);
            character = v.findViewById(R.id.characterName);
        }
    }
}
