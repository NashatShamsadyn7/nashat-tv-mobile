package com.nashat.tv.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.nashat.tv.R;
import androidx.annotation.Nullable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.DataSource;

import java.net.URI;

public final class ImageLoader {

    private ImageLoader() {}

    public static void load(ImageView view, String url) {
        if (view == null) return;

        Context ctx = view.getContext();
        RequestBuilder<Drawable> builder;

        if (url != null && !url.trim().isEmpty()) {
            GlideUrl glideUrl = new GlideUrl(
                    url,
                    new LazyHeaders.Builder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                            .addHeader("Referer", getBase(url))
                            .build()
            );
            builder = Glide.with(ctx).load(glideUrl);
        } else {
            builder = Glide.with(ctx).load((Object) null);
        }

        RequestOptions opts = new RequestOptions()
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .fallback(R.mipmap.ic_launcher_round)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .format(DecodeFormat.PREFER_ARGB_8888)
                .centerInside();

        builder
                .apply(opts)
                .apply(RequestOptions.timeoutOf(15000))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        // Fallback: try domain favicon to ensure something meaningful is shown
                        loadFavicon(view, url);
                        return true; // we start a new request, consume this failure
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false; // let Glide handle setting the resource
                    }
                })
                .into(view);
    }
    
    private static void loadFavicon(ImageView view, String originalUrl) {
        try {
            String base = getBase(originalUrl); // e.g. https://example.com/
            // Reliable, cached favicon provider over HTTPS
            String s2 = "https://www.google.com/s2/favicons?sz=128&domain_url=" + base;
            Glide.with(view.getContext())
                    .load(s2)
                    .apply(new RequestOptions()
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .centerInside()
                            .format(DecodeFormat.PREFER_ARGB_8888))
                    .into(view);
        } catch (Exception ignored) {
        }
    }
 
    private static String getBase(String s) {
        try {
            URI u = URI.create(s);
            String scheme = u.getScheme() == null ? "https" : u.getScheme();
            String host = u.getHost();
            if (host == null) return "https://";
            return scheme + "://" + host + "/";
        } catch (Exception e) {
            return "https://";
        }
    }
}