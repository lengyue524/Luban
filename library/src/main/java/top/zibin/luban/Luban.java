package top.zibin.luban;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static top.zibin.luban.Preconditions.checkNotNull;

public class Luban {

    public static final int FIRST_GEAR = 1;
    public static final int THIRD_GEAR = 3;

    private static final String TAG = "Luban";

    private static volatile Luban INSTANCE;

    private OnCompressListener compressListener;
    private IImageInfo mImageInfo;
    private int gear = THIRD_GEAR;

    public static Luban get() {
        if (INSTANCE == null) INSTANCE = new Luban();
        return INSTANCE;
    }

    public Luban launch() {
        checkNotNull(mImageInfo, "the image file cannot be null, please call .load() before this method!");

        if (compressListener != null) compressListener.onStart();

        if (gear == Luban.FIRST_GEAR)
            Observable.just(firstCompress())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (compressListener != null) compressListener.onError(throwable);
                        }
                    })
                    .onErrorResumeNext(Observable.<byte[]>empty())
                    .filter(new Func1<byte[], Boolean>() {
                        @Override
                        public Boolean call(byte[] bytes) {
                            return bytes != null;
                        }
                    })
                    .subscribe(new Action1<byte[]>() {
                        @Override
                        public void call(byte[] bytes) {
                            if (compressListener != null) compressListener.onSuccess(bytes);
                        }
                    });
        else if (gear == Luban.THIRD_GEAR)
            Observable.just(thirdCompress())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (compressListener != null) compressListener.onError(throwable);
                        }
                    })
                    .onErrorResumeNext(Observable.<byte[]>empty())
                    .filter(new Func1<byte[], Boolean>() {
                        @Override
                        public Boolean call(byte[] bytes) {
                            return bytes != null;
                        }
                    })
                    .subscribe(new Action1<byte[]>() {
                        @Override
                        public void call(byte[] bytes) {
                            if (compressListener != null) compressListener.onSuccess(bytes);
                        }
                    });

        return this;
    }

    public Luban load(IImageInfo imageInfo) {
        mImageInfo = imageInfo;
        return this;
    }

    public Luban setCompressListener(OnCompressListener listener) {
        compressListener = listener;
        return this;
    }

    public Luban putGear(int gear) {
        this.gear = gear;
        return this;
    }

    public Observable<byte[]> asObservable() {
        if (gear == FIRST_GEAR)
            return Observable.just(firstCompress());
        else if (gear == THIRD_GEAR)
            return Observable.just(thirdCompress());
        else return Observable.empty();
    }

    private byte[] thirdCompress() {
        double size;

        int angle = mImageInfo.getImageSpinAngle();
        int width = mImageInfo.getWidth();
        int height = mImageInfo.getHeight();
        int thumbW = width % 2 == 1 ? width + 1 : width;
        int thumbH = height % 2 == 1 ? height + 1 : height;

        width = thumbW > thumbH ? thumbH : thumbW;
        height = thumbW > thumbH ? thumbW : thumbH;

        double scale = ((double) width / height);

        if (scale <= 1 && scale > 0.5625) {
            if (height < 1664) {
                size = (width * height) / Math.pow(1664, 2) * 150;
                size = size < 60 ? 60 : size;
            } else if (height >= 1664 && height < 4990) {
                thumbW = width / 2;
                thumbH = height / 2;
                size = (thumbW * thumbH) / Math.pow(2495, 2) * 300;
                size = size < 60 ? 60 : size;
            } else if (height >= 4990 && height < 10240) {
                thumbW = width / 4;
                thumbH = height / 4;
                size = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                size = size < 100 ? 100 : size;
            } else {
                int multiple = height / 1280 == 0 ? 1 : height / 1280;
                thumbW = width / multiple;
                thumbH = height / multiple;
                size = (thumbW * thumbH) / Math.pow(2560, 2) * 300;
                size = size < 100 ? 100 : size;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            int multiple = height / 1280 == 0 ? 1 : height / 1280;
            thumbW = width / multiple;
            thumbH = height / multiple;
            size = (thumbW * thumbH) / (1440.0 * 2560.0) * 200;
            size = size < 100 ? 100 : size;
        } else {
            int multiple = (int) Math.ceil(height / (1280.0 / scale));
            thumbW = width / multiple;
            thumbH = height / multiple;
            size = ((thumbW * thumbH) / (1280.0 * (1280 / scale))) * 500;
            size = size < 100 ? 100 : size;
        }

        return compress(thumbW, thumbH, angle, (long) size);
    }

    private byte[] firstCompress() {
        int minSize = 60;
        int longSide = 720;
        int shortSide = 1280;

        long size = 0;
        long maxSize = mImageInfo.getSize() / 5;

        int angle = mImageInfo.getImageSpinAngle();
        int imageWidth = mImageInfo.getWidth();
        int imgHeight = mImageInfo.getHeight();
        int width = 0, height = 0;
        if (imageWidth <= imgHeight) {
            double scale = (double) imageWidth / (double) imgHeight;
            if (scale <= 1.0 && scale > 0.5625) {
                width = imageWidth > shortSide ? shortSide : imageWidth;
                height = width * imgHeight / imageWidth;
                size = minSize;
            } else if (scale <= 0.5625) {
                height = imgHeight > longSide ? longSide : imgHeight;
                width = height * imageWidth / imgHeight;
                size = maxSize;
            }
        } else {
            double scale = (double) imgHeight / (double) imageWidth;
            if (scale <= 1.0 && scale > 0.5625) {
                height = imgHeight > shortSide ? shortSide : imgHeight;
                width = height * imageWidth / imgHeight;
                size = minSize;
            } else if (scale <= 0.5625) {
                width = imageWidth > longSide ? longSide : imageWidth;
                height = width * imgHeight / imageWidth;
                size = maxSize;
            }
        }

        return compress(width, height, angle, size);
    }

    /**
     * obtain the thumbnail that specify the size
     *
     * @param width  the width of thumbnail
     * @param height the height of thumbnail
     * @return {@link Bitmap}
     */
    private Bitmap compress(int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        mImageInfo.decode(options);

        int outH = options.outHeight;
        int outW = options.outWidth;
        int inSampleSize = 1;

        if (outH > height || outW > width) {
            int halfH = outH / 2;
            int halfW = outW / 2;

            while ((halfH / inSampleSize) > height && (halfW / inSampleSize) > width) {
                inSampleSize *= 2;
            }
        }

        options.inSampleSize = inSampleSize;

        options.inJustDecodeBounds = false;

        int heightRatio = (int) Math.ceil(options.outHeight / (float) height);
        int widthRatio = (int) Math.ceil(options.outWidth / (float) width);

        if (heightRatio > 1 || widthRatio > 1) {
            if (heightRatio > widthRatio) {
                options.inSampleSize = heightRatio;
            } else {
                options.inSampleSize = widthRatio;
            }
        }
        options.inJustDecodeBounds = false;

        return mImageInfo.decode(options);
    }

    /**
     * 指定参数压缩图片
     * create the thumbnail with the true rotate angle
     *
     * @param width  width of thumbnail
     * @param height height of thumbnail
     * @param angle  rotation angle of thumbnail
     * @param size   the file size of image
     */
    private byte[] compress(int width, int height, int angle, long size) {
        Bitmap thbBitmap = compress(width, height);
        return toByte(thbBitmap, angle, size);
    }

    /**
     * 旋转图片
     * rotate the image with specified angle
     *
     * @param angle  the angle will be rotating 旋转的角度
     * @param bitmap target image               目标图片
     */
    private static Bitmap rotatingImage(int angle, Bitmap bitmap) {
        //rotate image
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        //create a new image
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Bitmap转换为字节数组
     * @param bitmap the image what be save   目标图片
     * @param angle  rotation angle of thumbnail
     * @param size   the file size of image   期望大小
     * @return
     */
    private byte[] toByte(Bitmap bitmap, int angle, long size) {
        checkNotNull(bitmap, TAG + "bitmap cannot be null");

        bitmap = rotatingImage(angle, bitmap);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int options = 100;
        bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);

        while (stream.toByteArray().length / 1024 > size) {
            stream.reset();
            options -= 6;
            bitmap.compress(Bitmap.CompressFormat.JPEG, options, stream);
        }
        return stream.toByteArray();
    }

    /**
     * 保存图片到指定路径
     * Save image with specified size
     * @param bytes 图片字节数组
     */
    public static File saveImage(String path, byte[] bytes) {
        File result = new File(path.substring(0, path.lastIndexOf("/")));
        if (!result.exists() && !result.mkdirs()) return null;

        try {
            FileOutputStream fos = new FileOutputStream(path);
            fos.write(bytes);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new File(path);
    }

    public static Bitmap toBitmap(byte[] bytes) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        return BitmapFactory.decodeStream(byteArrayInputStream);
    }
}