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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.List;

public class HorizontalMovieAdapter extends RecyclerView.Adapter<HorizontalMovieAdapter.ViewHolder> {

    private List<Movie> movies;
    private OnMovieClickListener listener;

    public interface OnMovieClickListener {
        void onMovieClick(Movie movie);
    }

    public HorizontalMovieAdapter(List<Movie> movies, OnMovieClickListener listener) {
        this.movies = movies;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_horizontal, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Movie movie = movies.get(position);
        
        if (holder.txtName != null) holder.txtName.setText(movie.getName());
        if (holder.txtCategory != null) holder.txtCategory.setText(movie.getCategory() == null || movie.getCategory().isEmpty() ? movie.getYear() : movie.getCategory());
        
        if (movie.getPoster() != null && !movie.getPoster().isEmpty() && !movie.getPoster().endsWith("null")) {
            Glide.with(holder.itemView.getContext())
                 .load(movie.getPoster())
                 .transform(new CenterCrop(), new RoundedCorners(16))
                 .into(holder.imgPoster);
        } else {
            holder.imgPoster.setImageResource(R.mipmap.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> listener.onMovieClick(movie));
    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPoster;
        TextView txtName;
        TextView txtCategory;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPoster = itemView.findViewById(R.id.imgPoster);
            txtName = itemView.findViewById(R.id.txtMovieName);
            txtCategory = itemView.findViewById(R.id.txtMovieCategory);
        }
    }
}
