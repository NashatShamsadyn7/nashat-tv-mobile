package com.nashat.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nashat.tv.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface Listener {
        void onWatchClicked(Channel channel);
        void onFavoriteToggled(Channel channel, boolean isFavorite);
    }

    private final List<Channel> data = new ArrayList<>();
    private final FavoritesStore favoritesStore;
    private final Listener listener;

    public ChannelAdapter(FavoritesStore favoritesStore, Listener listener) {
        this.favoritesStore = favoritesStore;
        this.listener = listener;
    }

    public void submitList(List<Channel> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public Channel getItem(int position) {
        return data.get(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Channel c = data.get(position);

        // Load the logo with beautiful styling
        ImageLoader.load(h.imgLogo, c.getLogo());

        // Set channel name
        h.txtChannelName.setText(c.getName());

        // Accessibility content descriptions
        h.imgLogo.setContentDescription(c.getName());
        h.itemView.setContentDescription("Watch " + c.getName());

        // Beautiful tap animation
        View.OnClickListener open = v -> {
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100);
                        if (listener != null && c.hasStream()) listener.onWatchClicked(c);
                    })
                    .start();
        };
        h.itemView.setOnClickListener(open);
        h.imgLogo.setOnClickListener(open);
        h.txtChannelName.setOnClickListener(open);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgLogo;
        TextView txtChannelName;
        TextView txtLogoFallback;

        VH(@NonNull View itemView) {
            super(itemView);
            imgLogo = itemView.findViewById(R.id.imgLogo);
            txtChannelName = itemView.findViewById(R.id.txtChannelName);
            txtLogoFallback = itemView.findViewById(R.id.txtLogoFallback);
        }
    }
}