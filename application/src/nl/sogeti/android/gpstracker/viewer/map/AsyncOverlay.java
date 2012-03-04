package nl.sogeti.android.gpstracker.viewer.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

public abstract class AsyncOverlay extends Overlay implements OverlayProvider
{

   private static final String TAG = "GG.AsyncOverlay";

   /**
    * Handler provided by the MapActivity to recalculate graphics
    */
   private Handler mHandler;

   private GeoPoint mGeoTopLeft;

   private GeoPoint mGeoBottumRight;

   private int mWidth;

   private int mHeight;

   private Bitmap mActiveBitmap;

   private GeoPoint mActiveTopLeft;

   private Point mActivePointTopLeft;

   private Bitmap mCalculationBitmap;

   private Canvas mCalculationCanvas;

   private Paint mPaint;

   private LoggerMap mLoggerMap;

   SegmentOsmOverlay mOsmOverlay;

   private SegmentMapQuestOverlay mMapQuestOverlay;

   private int mActiveZoomLevel;

   private Runnable mBitmapUpdater = new Runnable()
   {
      public void run()
      {
         postedBitmapUpdater = false;
         mCalculationBitmap.eraseColor(Color.TRANSPARENT);
         redrawOffscreen(mCalculationCanvas, mLoggerMap);
         synchronized (mActiveBitmap)
         {
            Bitmap oldActiveBitmap = mActiveBitmap;
            mActiveBitmap = mCalculationBitmap;
            mActiveTopLeft = mGeoTopLeft;
            mCalculationBitmap = oldActiveBitmap;
            mCalculationCanvas.setBitmap(mCalculationBitmap);
         }
         mLoggerMap.postInvalidate();
      }
   };

   private boolean postedBitmapUpdater;

   AsyncOverlay(LoggerMap loggermap, Handler handler)
   {
      mLoggerMap = loggermap;
      mHandler = handler;
      mWidth = 1;
      mHeight = 1;
      mPaint = new Paint();
      mActiveZoomLevel = -1;
      mActiveBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
      mActiveTopLeft = new GeoPoint(0, 0);
      mActivePointTopLeft = new Point();
      mCalculationBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
      mCalculationCanvas = new Canvas(mCalculationBitmap);
      
      mOsmOverlay = new SegmentOsmOverlay(mLoggerMap.getActivity(), mLoggerMap, this);
      mMapQuestOverlay = new SegmentMapQuestOverlay(this);
   }

   protected void considerRedrawOffscreen()
   {
      GeoPoint oldTopLeft = mGeoTopLeft;
      GeoPoint oldBottumRight = mGeoBottumRight;
      int oldZoomLevel = mActiveZoomLevel;
      
      mGeoTopLeft = mLoggerMap.fromPixels(0, 0);
      mGeoBottumRight = mLoggerMap.fromPixels(mWidth, mHeight);
      mActiveZoomLevel = mLoggerMap.getZoomLevel();
      
      boolean needNewCalculation = false;
      
      if (mCalculationBitmap.getWidth() != mWidth || mCalculationBitmap.getHeight() != mHeight)
      {
         mCalculationBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
         mCalculationCanvas.setBitmap(mCalculationBitmap);
         needNewCalculation = true;
      }

      if ( needNewCalculation 
            || mActiveZoomLevel != oldZoomLevel 
            || oldTopLeft == null || oldBottumRight == null || mGeoTopLeft.getLatitudeE6() / 100 != oldTopLeft.getLatitudeE6() / 100
            || mGeoTopLeft.getLongitudeE6() / 100 != oldTopLeft.getLongitudeE6() / 100
            || mGeoBottumRight.getLatitudeE6() / 100 != oldBottumRight.getLatitudeE6() / 100
            || mGeoBottumRight.getLongitudeE6() / 100 != oldBottumRight.getLongitudeE6() / 100)
      {
         scheduleRecalculation();
      }
   }
   
   public void onDateOverlayChanged()
   {
      if( !postedBitmapUpdater)
      {
         postedBitmapUpdater = true;
         mHandler.post(mBitmapUpdater);
      }
   }
   
   protected abstract void redrawOffscreen(Canvas asyncBuffer, LoggerMap loggermap);

   protected abstract void scheduleRecalculation();
   
   /**
    * {@inheritDoc}
    */
   @Override
   public void draw(Canvas canvas, MapView mapView, boolean shadow)
   {
      if (!shadow)
      {
         draw(canvas);
      }
   }

   private void draw(Canvas canvas)
   {
      mWidth = canvas.getWidth();
      mHeight = canvas.getHeight();
      considerRedrawOffscreen();
      
      if(mActiveBitmap.getWidth() > 1)
      {
         synchronized (mActiveBitmap)
         {
            mLoggerMap.toPixels(mActiveTopLeft, mActivePointTopLeft);
            canvas.drawBitmap(mActiveBitmap, mActivePointTopLeft.x, mActivePointTopLeft.y, mPaint);
         }
      }
   }

   protected boolean isPointOnScreen(Point point)
   {
      return point.x < 0 || point.y < 0 || point.x > mWidth || point.y > mHeight;
   }
   
   /**************************************/
   /**   Multi map support              **/
   /**************************************/
   
   public Overlay getGoogleOverlay()
   {
      return this;
   }

   public org.osmdroid.views.overlay.Overlay getOSMOverlay()
   {
      return mOsmOverlay;
   }
   
   public com.mapquest.android.maps.Overlay getMapQuestOverlay()
   {
      return mMapQuestOverlay;
   }
   
   protected abstract boolean commonOnTap(GeoPoint tappedGeoPoint);

   static class SegmentOsmOverlay extends org.osmdroid.views.overlay.Overlay
   {
      AsyncOverlay mSegmentOverlay ;
      LoggerMap mLoggerMap;
      
      public SegmentOsmOverlay(Context ctx, LoggerMap map, AsyncOverlay segmentOverlay)
      {
         super(ctx);
         mLoggerMap = map;
         mSegmentOverlay = segmentOverlay;
      }

      public AsyncOverlay getSegmentOverlay()
      {
         return mSegmentOverlay;
      }
      
      @Override
      public boolean onSingleTapUp(MotionEvent e, org.osmdroid.views.MapView openStreetMapView) 
      {
         int x = (int) e.getX();
         int y = (int) e.getY();
         GeoPoint tappedGeoPoint = mLoggerMap.fromPixels(x, y);
         return mSegmentOverlay.commonOnTap(tappedGeoPoint );
      }

      @Override
      protected void draw(Canvas canvas, org.osmdroid.views.MapView view, boolean shadow)
      {
         if( !shadow )
         {
            mSegmentOverlay.draw( canvas );
         }
      }      
   }

   static class SegmentMapQuestOverlay extends com.mapquest.android.maps.Overlay
   {
      AsyncOverlay mSegmentOverlay ;
      
      public SegmentMapQuestOverlay(AsyncOverlay segmentOverlay)
      {
         super();
         mSegmentOverlay = segmentOverlay;
      }

      public AsyncOverlay getSegmentOverlay()
      {
         return mSegmentOverlay;
      }
      
      @Override
      public boolean onTap(com.mapquest.android.maps.GeoPoint p, com.mapquest.android.maps.MapView mapView)
      {
         GeoPoint tappedGeoPoint = new GeoPoint(p.getLatitudeE6(), p.getLongitudeE6());
         return mSegmentOverlay.commonOnTap(tappedGeoPoint );
      }
      
      @Override
      public void draw(Canvas canvas, com.mapquest.android.maps.MapView mapView, boolean shadow)
      {
         if( !shadow )
         {
            mSegmentOverlay.draw( canvas );
         }
      }
      
   }
}
