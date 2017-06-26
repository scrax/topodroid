/* @file SketchDrawingSurface.java
 *
 * @author marco corvi
 * @date jan 2013
 *
 * @brief TopoDroid 3d sketch: drawing surface (canvas)
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.EOFException;

import android.util.Log;

/**
 */
public class SketchDrawingSurface extends SurfaceView
                                  implements SurfaceHolder.Callback
{
  static final String TAG = "DistoX";

    private Boolean _run;
    protected DrawThread mDrawingThread;
    private Bitmap mBitmap;
    public boolean isDrawing = true;
    public DrawingPath previewPath;
    private SurfaceHolder mHolder; // canvas holder
    private Context mContext;
    private AttributeSet mAttrs;
    private int mWidth;            // canvas width
    private int mHeight;           // canvas height

    // private DrawingCommandManager commandManager;
    private SketchModel mModel;

    public int width()  { return mWidth; }
    public int height() { return mHeight; }

    public SketchDrawingSurface(Context context, AttributeSet attrs) 
    {
      super(context, attrs);
      mWidth = 0;
      mHeight = 0;

      mDrawingThread = null;
      mContext = context;
      mAttrs   = attrs;
      mHolder = getHolder();
      mHolder.addCallback(this);
      // commandManager = new DrawingCommandManager();
    }

    // public void setDisplayMode( int mode ) { commandManager.setDisplayMode(mode); }
    // public int getDisplayMode( ) { return commandManager.getDisplayMode(); }

    void setModel( SketchModel model ) { mModel = model; }

    void refresh()
    {
      Canvas canvas = null;
      try {
        canvas = mHolder.lockCanvas();
        if ( mBitmap == null ) {
          mBitmap = Bitmap.createBitmap (1, 1, Bitmap.Config.ARGB_8888);
        }
        final Canvas c = new Canvas (mBitmap);
        mWidth  = c.getWidth();
        mHeight = c.getHeight();

        c.drawColor(0, PorterDuff.Mode.CLEAR);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        // commandManager.executeAll( c, previewDoneHandler );
        mModel.executeAll( c, null /* previewDoneHandler */ ); // handler is not used
        if ( previewPath != null ) {
          previewPath.draw(c, null);
        }
      
        canvas.drawBitmap (mBitmap, 0, 0, null);
      } finally {
        if ( canvas != null ) {
          mHolder.unlockCanvasAndPost( canvas );
        }
      }
    }

    // private Handler previewDoneHandler = new Handler()
    // {
    //   @Override
    //   public void handleMessage(Message msg) {
    //     isDrawing = false;
    //   }
    // };

    class DrawThread extends  Thread
    {
      private SurfaceHolder mSurfaceHolder;

      public DrawThread(SurfaceHolder surfaceHolder)
      {
        mSurfaceHolder = surfaceHolder;
      }

      public void setRunning(boolean run)
      {
        _run = run;
      }

      @Override
      public void run() 
      {
        while ( _run ) {
          if ( isDrawing ) {
            refresh();
          }
        }
      }
    }

    void stopDrawing() 
    {
      isDrawing = false;
      if ( mDrawingThread != null ) {
        mDrawingThread.setRunning( false );
        try {
          mDrawingThread.join();
        } catch ( InterruptedException e ) { }
        mDrawingThread = null;
      }
    }

    // (x,y,z) world coords.
    // public SketchStationName addStation( String name, float x, float y, float z )
    // {
    //   SketchStationName st = new SketchStationName(name, x, y, z );
    //   st.mPaint = BrushManager.fixedStationPaint;
    //   mModel.addFixedStation( st );
    //   return st;
    // }

    public void clearReferences() 
    { 
      // commandManager.clearReferences();
      if ( mModel != null ) {
        mModel.clearReferences();
      }
    }

    public void undo()
    {
      isDrawing = true;
      mModel.undo();
    }

    // public boolean hasMoreUndo() { return commandManager.hasMoreUndo(); }


    // public boolean hasStationName( String name ) { return commandManager.hasStationName( name ); }
    // public DrawingStationName  getStationAt( float x, float y, float size ) 
    // { return commandManager.getStationAt( x, y, size ); }

    public void surfaceChanged(SurfaceHolder mHolder, int format, int width,  int height) 
    {
      mBitmap =  Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);;
    }

    void setThreadRunning( boolean running ) 
    {
      if ( mDrawingThread != null ) {
        if ( ! _run ) {
          mDrawingThread.setRunning(true);
          mDrawingThread.start();
        }
      }
    }

    public void surfaceCreated(SurfaceHolder mHolder) 
    {
      if (mDrawingThread == null ) {
        mDrawingThread = new DrawThread(mHolder);
      }
      // Log.v("DistoX", "surface created set running true");
      mDrawingThread.setRunning(true);
      mDrawingThread.start();
    }

    public void surfaceDestroyed(SurfaceHolder mHolder) 
    {
      if ( mDrawingThread != null ) {
        boolean retry = true;
        mDrawingThread.setRunning(false);
        while (retry) {
          try {
            mDrawingThread.join();
            retry = false;
          } catch (InterruptedException e) {
            // we will try it again and again...
          }
        }
        mDrawingThread = null;
      }
      // Log.v("DistoX", "surface destroyed");
    }

}
