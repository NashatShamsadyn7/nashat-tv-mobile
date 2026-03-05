package com.nashat.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;

import java.util.List;

public class ActorAdapter extends RecyclerView.Adapter<ActorAdapter.ViewHolder> {

    public interface OnActorClickListener {
        void onActorClick(Actor actor);
    }

    private List<Actor> actors;
    private OnActorClickListener listener;

    public ActorAdapter(List<Actor> actors, OnActorClickListener listener) {
        this.actors = actors;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_actor, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Actor actor = actors.get(position);
        holder.txtName.setText(actor.getName());
        holder.txtCharacter.setText(actor.getCharacter());

        if (actor.getProfilePath() != null && !actor.getProfilePath().isEmpty() && !actor.getProfilePath().endsWith("null")) {
            Glide.with(holder.itemView.getContext())
                 .load(actor.getProfilePath())
                 .transform(new CenterCrop())
                 .into(holder.imgProfile);
        } else {
            holder.imgProfile.setImageResource(R.drawable.ic_person);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onActorClick(actor);
        });
    }

    @Override
    public int getItemCount() {
        return actors.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProfile;
        TextView txtName;
        TextView txtCharacter;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProfile = itemView.findViewById(R.id.imgActorProfile);
            txtName = itemView.findViewById(R.id.txtActorName);
            txtCharacter = itemView.findViewById(R.id.txtCharacterName);
        }
    }
}
