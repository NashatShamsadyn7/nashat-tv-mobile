package com.nashat.tv;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class FirebaseChannelAdapter extends RecyclerView.Adapter<FirebaseChannelAdapter.ChannelViewHolder> {

    private final Context context;
    private final List<Channel> channelList;

    public FirebaseChannelAdapter(Context context, List<Channel> channelList) {
        this.context = context;
        this.channelList = channelList;
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_channel_firebase, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = channelList.get(position);
        holder.name.setText(channel.getName());
        holder.category.setText(channel.getCategory());

        Glide.with(context).load(channel.getLogo()).into(holder.logo);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("url", channel.getUrl());
            intent.putExtra("name", channel.getName());
            intent.putExtra("logo", channel.getLogo());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return channelList != null ? channelList.size() : 0;
    }

    public static class ChannelViewHolder extends RecyclerView.ViewHolder {
        final TextView name, category;
        final ImageView logo;

        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            category = itemView.findViewById(R.id.tvCategory);
            logo = itemView.findViewById(R.id.ivLogo);
        }
    }
}