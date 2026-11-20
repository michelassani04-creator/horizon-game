package com.horizon.tv.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.horizon.tv.data.model.ContentItem;
import com.horizon.tv.player.PlayerActivity;

/** Fiche d'un film ou d'une série : image, titre, synopsis, lecture, ajout à Ma liste, épisodes. */
public class DetailFragment extends Fragment {

    public static DetailFragment of(ContentItem it) {
        DetailFragment f = new DetailFragment();
        Bundle b = new Bundle();
        b.putString("id", it.id);
        b.putString("type", it.type);
        b.putString("title", it.title);
        b.putString("subtitle", it.subtitle);
        b.putString("description", it.description);
        b.putString("imageUrl", it.imageUrl);
        b.putString("backdropUrl", it.backdropUrl);
        b.putBoolean("playable", it.playable);
        b.putBoolean("available", it.available);
        f.setArguments(b);
        return f;
    }

    private ContentItem item() {
        Bundle b = requireArguments();
        ContentItem it = new ContentItem(b.getString("id"), b.getString("type"), b.getString("title"),
                b.getString("subtitle"), b.getBoolean("playable"));
        it.description = b.getString("description");
        it.imageUrl = b.getString("imageUrl");
        it.backdropUrl = b.getString("backdropUrl");
        it.available = b.getBoolean("available", true);
        return it;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        final android.content.Context c = requireContext();
        final ContentItem it = item();
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Image large (16:9) avec fondu vers le fond
        int w = c.getResources().getDisplayMetrics().widthPixels;
        FrameLayout hero = new FrameLayout(c);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(Ui.gradientFor(it.id + it.title));
        String url = it.backdropUrl != null && !it.backdropUrl.isEmpty() ? it.backdropUrl : it.imageUrl;
        if (url != null && !url.isEmpty()) Glide.with(this).load(url).into(img);
        hero.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View fade = new View(c);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, Ui.BG}));
        hero.addView(fade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w * 9 / 16));

        int p = Ui.dp(c, 20);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, 0, p, p);
        col.addView(body, Ui.matchWrap());

        body.addView(Ui.text(c, it.title == null ? "" : it.title, 24, Ui.TEXT, true), Ui.matchWrap());
        if (it.subtitle != null && !it.subtitle.isEmpty()) {
            TextView s = Ui.text(c, it.subtitle, 14, Ui.MUTED, false);
            s.setPadding(0, Ui.dp(c, 4), 0, 0);
            body.addView(s, Ui.matchWrap());
        }

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 8));
        boolean isSeries = "series".equals(it.type);
        if (it.available && (isSeries || it.playable)) {
            MaterialButton main = Ui.button(c, isSeries ? "Voir les épisodes" : "Lire");
            Anim.pressable(main);
            main.setOnClickListener(v -> {
                if (isSeries) {
                    ((MainActivity) requireActivity()).push(SimpleListFragment.episodes(it.id, it.title));
                } else {
                    PlayerActivity.start(requireActivity(), it.type, it.id, it.title);
                }
            });
            row.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        MaterialButton fav = Ui.button(c, "+ Ma liste");
        Anim.pressable(fav);
        fav.setOnClickListener(v -> Nav.toggleFavorite(requireActivity(), it));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fp.leftMargin = Ui.dp(c, 10);
        row.addView(fav, fp);
        body.addView(row, Ui.matchWrap());

        if (!it.available) {
            TextView n = Ui.text(c, "Ce contenu n'est pas disponible dans votre pays ou n'est plus diffusable.",
                    14, Ui.RED, false);
            n.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            body.addView(n, Ui.matchWrap());
        } else if (!isSeries && !it.playable) {
            TextView n = Ui.text(c, "Ce contenu n'est pas encore lisible.", 14, Ui.MUTED, false);
            n.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            body.addView(n, Ui.matchWrap());
        }

        if (it.description != null && !it.description.isEmpty()) {
            TextView d = Ui.text(c, it.description, 15, Ui.TEXT, false);
            d.setLineSpacing(0, 1.2f);
            d.setPadding(0, Ui.dp(c, 8), 0, 0);
            body.addView(d, Ui.matchWrap());
        }
        Anim.fadeIn(body, 350);
        return scroll;
    }
}
