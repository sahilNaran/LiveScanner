package za.co.knuckles.livescanner;

import android.graphics.Bitmap;

public interface IScanner {
    void displayHint(Hints scanHint);
    void onPictureClicked(Bitmap bitmap);
}
