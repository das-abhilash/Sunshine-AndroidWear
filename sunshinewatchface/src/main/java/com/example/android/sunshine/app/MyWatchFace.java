/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;

import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface THIN_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC);


    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;
    long mInteractiveUpdateRateMs = INTERACTIVE_UPDATE_RATE_MS;
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    public static final String PATH_WITH_WEATHER = "/weather";
    private static final String COLON_STRING = ":";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        int i = 10;
        Paint mTextPaint;
        boolean mAmbient;
        String mAmString;
        String mPmString;
        int weatherId;

        String Tmax = null;
        String Tmin = null;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mAmPmPaint;
        Paint mColonPaint;
        float mLineHeight;
        float mLineWidth;
        boolean mShouldDrawColons;
        Calendar mCalendar;
        Date mDate;
        float mColonWidth;
        java.text.DateFormat mDateFormat;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        Bitmap ic_clear, ic_storm, ic_light_rain, ic_rain, ic_snow, ic_fog, ic_light_clouds, ic_cloudy;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mLineWidth = resources.getDimension(R.dimen.digital_line_width);
            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);

            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date), THIN_TYPEFACE);
            mHourPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mMinutePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mSecondPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_am_pm));
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons));
            mCalendar = Calendar.getInstance();
            initFormats();
            mDate = new Date();
            ic_clear = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_clear);
            ic_storm = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_clear);

            ic_light_rain = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_light_rain);
            ic_rain = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_rain);
            ic_snow = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_snow);
            ic_rain = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_rain);
            ic_snow = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_snow);
            ic_fog = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_fog);
            ic_storm = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_storm);
            ic_clear = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_clear);
            ic_light_clouds = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_light_clouds);
            ic_cloudy = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_cloudy);

        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat("EE,  dd MMM yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }


        private Paint createTextPaint(int textColor) {
            return createTextPaint(textColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();


                mGoogleApiClient.connect();
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset_square);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mColonWidth = mTextPaint.measureText(COLON_STRING);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;
            if (inAmbientMode) {
                mDatePaint.setColor(getColor(R.color.digital_text));
                mColonPaint.setColor(getColor(R.color.digital_text));
            } else {
                mDatePaint.setColor(getColor(R.color.digital_date));
                mColonPaint.setColor(getColor(R.color.digital_colons));
            }
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
                mDatePaint.setAntiAlias(!inAmbientMode);
                mHourPaint.setAntiAlias(!inAmbientMode);
                mMinutePaint.setAntiAlias(!inAmbientMode);
                mSecondPaint.setAntiAlias(!inAmbientMode);
                mAmPmPaint.setAntiAlias(!inAmbientMode);
                mColonPaint.setAntiAlias(!inAmbientMode);
            }

            invalidate();
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(MyWatchFace.this);

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the hours.
            float x = (float) (mXOffset + mLineWidth);

            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);


            if (!isInAmbientMode()) {

                if (mShouldDrawColons) {
                    canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
                }
                x += mColonWidth;
                canvas.drawText(formatTwoDigitNumber(
                        mCalendar.get(Calendar.SECOND)), x, mYOffset, mSecondPaint);
            } else if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(getAmPmString(
                        mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            }


            canvas.drawText(
                    mDateFormat.format(mDate),
                    (float) (mXOffset + mLineWidth*0.6), mYOffset + mLineHeight, mDatePaint);


            Bitmap bitmap = getIconResourceForWeatherCondition(weatherId);
            double mf = 1;
            if (getPeekCardPosition().isEmpty() && bitmap != null) {
                if (!isInAmbientMode()) {
                    canvas.drawBitmap(bitmap, mXOffset - mLineWidth, (float) (mYOffset + mLineHeight * 1.8), null);
                    canvas.drawLine(mXOffset + mLineWidth * 3, (float) (mYOffset + mLineHeight * 1.2), mXOffset + mLineWidth * 4,
                            (float) (mYOffset + mLineHeight * 1.2), mDatePaint);
                    mf = 2.5;
                }

                canvas.drawText(Tmax + "  " + Tmin, (float) (mXOffset + mLineWidth * mf),
                        (float) (mYOffset + mLineHeight * 3.2), mTextPaint);

            }

        }

        private Bitmap getIconResourceForWeatherCondition(int weatherId) {
            if (weatherId >= 200 && weatherId <= 232) {
                return ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return ic_rain;
            } else if (weatherId == 511) {
                return ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return ic_storm;
            } else if (weatherId == 800) {
                return ic_clear;
            } else if (weatherId == 801) {
                return ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return ic_cloudy;
            }
            return null;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = mInteractiveUpdateRateMs
                        - (timeMs % mInteractiveUpdateRateMs);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void getData(String data){
            String[] weather = data.split("`");
            weatherId = Integer.parseInt(weather[0]);
            Tmax =weather[1];
            Tmin =weather[2];
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateDataItemAndUiOnStartup();
        }

        private void updateDataItemAndUiOnStartup() {
            PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient);
            results.setResultCallback(new ResultCallback<DataItemBuffer>() {
                @Override
                public void onResult(DataItemBuffer dataItems) {
                    if (dataItems.getCount() != 0) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItems.get(0));

                        String data = dataMapItem.getDataMap().getString("key");
                        getData(data);
                    }

                    dataItems.release();
                }
            });
        }


        @Override
        public void onConnectionSuspended(int e) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            i++;
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        MyWatchFace.PATH_WITH_WEATHER)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                String data = dataMapItem.getDataMap().getString("key");
                getData(data);
            }

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }
    }
}
