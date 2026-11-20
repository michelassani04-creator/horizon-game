package com.horizon.tv.ui;

import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.horizon.tv.data.AuthStore;
import com.horizon.tv.data.db.AppDatabase;
import com.horizon.tv.data.db.FavoriteEntity;
import com.horizon.tv.data.db.HorizonDao;
import com.horizon.tv.data.model.ContentItem;
import com.horizon.tv.player.PlayerActivity;

/** Actions communes sur un élément de catalogue. */
public final class Nav {
    private Nav() {}

    public static void open(FragmentActivity a, ContentItem it) {
        if (it == null || "header".equals(it.type)) return;
        if ("series".equals(it.type) || "movie".equals(it.type)) {
            ((MainActivity) a).push(DetailFragment.of(it));
        } else if ("profile".equals(it.type)) {
            AuthStore.setProfile(a, it.id);
            Toast.makeText(a, "Profil actif : " + it.title, Toast.LENGTH_SHORT).show();
        } else if (it.playable && it.available) {
            String type = "channel".equals(it.type) ? "channel" : it.type;
            PlayerActivity.start(a, type, it.id, it.title);
        } else if (!it.available) {
            Toast.makeText(a, "Ce contenu est indisponible.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(a, "Ce contenu n'est pas lisible pour le moment.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void toggleFavorite(final FragmentActivity a, final ContentItem it) {
        if (it == null || "header".equals(it.type) || "menu".equals(it.type)) return;
        final String uid = AppDatabase.uid(a, it.type, it.id);
        AppDatabase.io(() -> {
            HorizonDao dao = AppDatabase.get(a).dao();
            final boolean added;
            if (dao.isFavorite(uid) > 0) {
                dao.deleteFavorite(uid);
                added = false;
            } else {
                FavoriteEntity e = new FavoriteEntity();
                e.uid = uid;
                e.id = it.id;
                e.type = it.type;
                e.title = it.title;
                e.imageUrl = it.imageUrl;
                dao.upsertFavorite(e);
                added = true;
            }
            a.runOnUiThread(() -> Toast.makeText(a,
                    added ? "Ajouté aux favoris" : "Retiré des favoris", Toast.LENGTH_SHORT).show());
        });
    }
}
