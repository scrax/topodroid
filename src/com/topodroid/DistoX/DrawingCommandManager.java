/* @file DrawingCommandManager.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid drawing: commands manager
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PointF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
// import android.graphics.Path.Direction;
import android.os.Handler;

import java.util.Iterator;
import java.util.List;
// import java.util.Locale;
import java.util.Collections;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
// import java.io.BufferedReader;
import java.io.IOException;
import java.io.EOFException;

import java.util.Locale;

import android.util.Log;

/**
 */
public class DrawingCommandManager 
{
  private static final int BORDER = 20;

  static int mDisplayMode = DisplayMode.DISPLAY_ALL; // this display mode is shared among command managers
  RectF mBBox;

  private DrawingPath mNorthLine;
  DrawingPath mFirstReference;
  DrawingPath mSecondReference;

  private List<DrawingPath>    mGridStack1;
  private List<DrawingPath>    mGridStack10;
  private List<DrawingPath>    mGridStack100;

  private DrawingScaleReference mScaleRef; /*[AR] this is the instance of scale reference line*/

  List<DrawingPath> GetGrid1()   { return mGridStack1; }
  List<DrawingPath> GetGrid10()  { return mGridStack10; }
  List<DrawingPath> GetGrid100() { return mGridStack100; }

  private List<DrawingPath>        mLegsStack;
  private List<DrawingPath>        mSplaysStack;
  private List<ICanvasCommand>     mCurrentStack;
  private List<DrawingStationPath> mUserStations;  // user-inserted stations
  private List<ICanvasCommand>     mRedoStack;
  // private List<DrawingPath>     mHighlight;  // highlighted path
  private List<DrawingStationName> mStations;  // survey stations
  private List<DrawingLinePath>    mScrap;     // scrap outline
  private int mMaxAreaIndex;                   // max index of areas in this plot

  private Selection mSelection;
  private SelectionSet mSelected;
  private boolean mDisplayPoints;

  private Matrix mMatrix;
  private float  mScale; // current zoom: value of 1 pl in scene space

  DrawingPath              getNorth()        { return mNorthLine;    }
  List<DrawingPath>        getLegs()         { return mLegsStack;    }
  List<DrawingPath>        getSplays()       { return mSplaysStack;  }
  List<ICanvasCommand>     getCommands()     { return mCurrentStack; }
  List<DrawingStationName> getStations()     { return mStations;     } 
  List<DrawingStationPath> getUserStations() { return mUserStations; }

  int mSelectMode = Drawing.FILTER_ALL;
  void setSelectMode( int mode ) { mSelectMode = mode; }

  /* Check if any line overlaps another of the same type
   * In case of overlap the overlapped line is removed
   */
  void checkLines()
  {
    synchronized( mCurrentStack ) {
      int size = mCurrentStack.size();
      for ( int i1 = 0; i1 < size; ++i1 ) {
        ICanvasCommand cmd1 = mCurrentStack.get( i1 );
        DrawingPath path1 = (DrawingPath)cmd1;
        if ( path1.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
        DrawingLinePath line1 = (DrawingLinePath)path1;
        for ( int i2 = 0; i2 < size; ++i2 ) {
          if ( i2 == i1 ) continue;
          ICanvasCommand cmd2 = mCurrentStack.get( i2 );
          DrawingPath path2 = (DrawingPath)cmd2;
          if ( path2.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
          DrawingLinePath line2 = (DrawingLinePath)path2;
          // if every point in line2 overlaps a point in line1 
          if ( line1.overlap( line1 ) == line2.size() ) {
            TDLog.Error("LINE OVERLAP " + i1 + "-" + i2 + " total nr. " + size );
            // for ( int i=0; i<size; ++i ) {
            //   ICanvasCommand cmd = mCurrentStack.get( i );
            //   DrawingPath path = (DrawingPath)cmd;
            //   if ( path.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
            //   DrawingLinePath line = (DrawingLinePath)path;
            //   line.dump();
            // }
            // Log.v("DistoX", "LINE1 ");
            // line1.dump();
            // Log.v("DistoX", "LINE2 ");
            // line2.dump();
            doDeletePath( line2 );
            -- size;
            -- i2;
            // throw new RuntimeException();
            if ( i2 < i1 ) --i1;
          }
        }
      }
    }
  }

  /* Flip the X-axis
   * flip the drawing about the vertical direction
   */
  private void flipXAxes( List<DrawingPath> paths )
  {
    final float z = 1/mScale;
    final Iterator i1 = paths.iterator();
    while ( i1.hasNext() ){
      final DrawingPath drawingPath = (DrawingPath) i1.next();
      drawingPath.flipXAxis( z );
    }
  }

  void flipXAxis( float z )
  {
    synchronized( mGridStack1 ) {
      flipXAxes( mGridStack1 );
      if ( mNorthLine != null ) mNorthLine.flipXAxis(z);
      flipXAxes( mGridStack10 );
      flipXAxes( mGridStack100 );
    }
    synchronized( mLegsStack )   { flipXAxes( mLegsStack ); }
    synchronized( mSplaysStack ) { flipXAxes( mSplaysStack ); }
    // FIXME 
    synchronized( mScrap ) { mScrap.clear(); }
 
    synchronized( mStations ) {
      for ( DrawingStationName st : mStations ) {
        st.flipXAxis(z);
      }
    }
    if ( mCurrentStack != null ) {
      Selection selection = new Selection();
      synchronized( mCurrentStack ) {
        final Iterator i = mCurrentStack.iterator();
        while ( i.hasNext() ){
          final ICanvasCommand cmd = (ICanvasCommand) i.next();
          if ( cmd.commandType() == 0 ) {
            cmd.flipXAxis(z);
            DrawingPath path = (DrawingPath)cmd;
            if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
              DrawingLinePath line = (DrawingLinePath)path;
              line.flipReversed();
            }
            selection.insertPath( path );
          }
        }
      }
      mSelection = selection;
    }
    synchronized( mUserStations ) {
      for ( DrawingStationPath p : mUserStations ) {
        p.flipXAxis(z);
      }
    }
  }

  /* Shift the drawing
   * translate the drawing by (x,y)
   */
  void shiftDrawing( float x, float y )
  {
    // if ( mStations != null ) {
    //   synchronized( mStations ) {
    //     for ( DrawingStationName st : mStations ) {
    //       st.shiftBy( x, y );
    //     }
    //   }
    // }
    if ( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        final Iterator i = mCurrentStack.iterator();
        while ( i.hasNext() ){
          final ICanvasCommand cmd = (ICanvasCommand) i.next();
          cmd.shiftPathBy( x, y );
        }
      }
    }
    if ( mSelection != null ) {
      synchronized( mSelection ) {
        mSelection.shiftSelectionBy( x, y );
      }
    }
  }

  public DrawingCommandManager()
  {
    mBBox = new RectF();
    mNorthLine       = null;
    mFirstReference  = null;
    mSecondReference = null;
    mGridStack1   = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mGridStack10  = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mGridStack100 = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mLegsStack    = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mSplaysStack  = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mScrap        = Collections.synchronizedList(new ArrayList<DrawingLinePath>());
    mCurrentStack = Collections.synchronizedList(new ArrayList<ICanvasCommand>());
    mUserStations = Collections.synchronizedList( new ArrayList<DrawingStationPath>());
    mRedoStack    = Collections.synchronizedList(new ArrayList<ICanvasCommand>());
    // mHighlight = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mStations     = Collections.synchronizedList(new ArrayList<DrawingStationName>());
    mMatrix       = new Matrix(); // identity
    mSelection    = new Selection();
    mSelected     = new SelectionSet();
    mMaxAreaIndex = 0;
  }

  void addScaleRef() 
  {
    mScaleRef = new DrawingScaleReference(new Point(20,-20), 0.33f);
  }

  // void debug()
  // {
  //   Log.v("DistoX", "Manager grid " + mGridStack1.toArray().length + " " 
  //                                   + mGridStack10.toArray().length + " " 
  //                                   + mGridStack100.toArray().length + " legs "
  //                                   + mLegsStack.toArray().length + " "
  //                                   + mSplaysStack.toArray().length + " items "
  //                                   + mCurrentStack.toArray().length );
  // }

  void clearSelected() { synchronized( mSelected ) { mSelected.clear(); } }

  void clearReferences()
  {
    synchronized( mGridStack1 ) {
      mNorthLine       = null;
      mFirstReference  = null;
      mSecondReference = null;
      mGridStack1.clear();
      mGridStack10.clear();
      mGridStack100.clear();
      mScaleRef = null;
    }

    synchronized( mLegsStack )   { mLegsStack.clear(); }
    synchronized( mSplaysStack ) { mSplaysStack.clear(); }
    synchronized( mScrap       ) { mScrap.clear(); }
    synchronized( mStations )    { mStations.clear(); }
    clearSelected();
    synchronized( mSelection )   { mSelection.clearReferencePoints(); }
  }

  private void clearSketchItems()
  {
    synchronized( mSelection )    { mSelection.clearSelectionPoints(); }
    synchronized( mCurrentStack ) { mCurrentStack.clear(); }
    synchronized( mUserStations ) { mUserStations.clear(); }
    mRedoStack.clear();
    mSelected.clear();
    mDisplayPoints = false;
  }

  void clearDrawing()
  {
    clearReferences();
    clearSketchItems();
    // mMatrix = new Matrix(); // identity
  }

  void setFirstReference( DrawingPath path ) { synchronized( mGridStack1 ) { mFirstReference = path; } }

  void setSecondReference( DrawingPath path ) { synchronized( mGridStack1 ) { mSecondReference = path; } }

  void addSecondReference( float x, float y ) { synchronized( mGridStack1 ) { mSecondReference.pathAddLineTo(x,y); } }

  /* the next index for the ID of the area border
   */
  int getNextAreaIndex()
  {
    ++mMaxAreaIndex;
    return mMaxAreaIndex;
  }

  /* return the list of shots that intesect the segment (p1--p2)
   */
  List< DrawingPath > getIntersectionShot( LinePoint p1, LinePoint p2 )
  {
    List< DrawingPath > ret = new ArrayList< DrawingPath >();
    for ( DrawingPath p : mLegsStack ) {
      if ( p.mType == DrawingPath.DRAWING_PATH_FIXED ) {
        if ( p.intersect( p1.x, p1.y, p2.x, p2.y, null ) ) {
          ret.add( p );
        }
      }
    }
    return ret;
  }

  /* Get the station at (x,y)
   * Return the station inside the square centered at (x,y) of side 2*size
   */
  DrawingStationName getStationAt( float x, float y, float size ) // x,y canvas coords
  {
    // Log.v("DistoX", "get station at " + x + " " + y );
    for ( DrawingStationName st : mStations ) {
      // Log.v("DistoX", "station at " + st.cx + " " + st.cy );
      if ( Math.abs( x - st.cx ) < size && Math.abs( y - st.cy ) < size ) return st;
    }
    return null;
  }

  void setDisplayPoints( boolean display ) { mDisplayPoints = display; }

  boolean isSelectable() { return mSelection != null; }

  // public void clearHighlight()
  // {
  //   for ( DrawingPath p : mHighlight ) {
  //     if ( p.mType == DrawingPath.DRAWING_PATH_FIXED ) {
  //       p.mPaint = BrushManager.fixedShotPaint;
  //     } else {
  //       p.mPaint = BrushManager.fixedSplayPaint;
  //     }
  //   }
  //   mHighlight.clear();
  // }

  // public DBlock setHighlight( int plot_type, float x, float y )
  // {
  //   clearHighlight();
  //   if ( ! PlotInfo.isSketch2d( plot_type ) ) return null;
  //   boolean legs   = (mDisplayMode & DisplayMode.DISPLAY_LEG) != 0;
  //   boolean splays = (mDisplayMode & DisplayMode.DISPLAY_SPLAY) != 0;
  //   if ( mHighlight.size() == 1 ) {
  //     return mHighlight.get(0).mBlock;
  //   }
  //   return null;
  // }

  /* Set the transform matrix for the canvas rendering of the drawing
   * The matrix is diag(s*dx, s*dy)
   */
  public void setTransform( float dx, float dy, float s )
  {
    mMatrix = new Matrix();
    mMatrix.postTranslate( dx, dy );
    mMatrix.postScale( s, s );
    mScale  = 1 / s;
    mBBox.left   = - dx;      // scene coords
    mBBox.right  = mScale * TopoDroidApp.mDisplayWidth - dx; 
    mBBox.top    = - dy;
    mBBox.bottom = mScale * TopoDroidApp.mDisplayHeight - dy;

    synchronized ( mCurrentStack ) {
      final Iterator i = mCurrentStack.iterator();
      while ( i.hasNext() ){
        final ICanvasCommand c = (ICanvasCommand) i.next();
        if ( c.commandType() == 0 ) {
          DrawingPath path = (DrawingPath)c;
          if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
            DrawingAreaPath area = (DrawingAreaPath)path;
            area.shiftShaderBy( dx, dy, s );
          }
        }
      }
    }

    // FIXME 
    // TUNING this is to see how many buckets are on the canvas and how many points they contain
    //
    // if ( mSelection != null ) {
    //   int cnt = 0;
    //   float pts = 0;
    //   for ( SelectionBucket bucket : mSelection.mBuckets ) {
    //     if ( bucket.intersects( mBBox ) ) { ++ cnt; pts += bucket.size(); }
    //   }
    //   pts /= cnt;
    //   Log.v("DistoX", "visible buckets " + cnt + " avg pts/bucket " + pts );
    // }
  }

  // oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

  void addEraseCommand( EraseCommand cmd )
  {
    mCurrentStack.add( cmd );
  }

  private boolean hasEraser = false;
  private float mEraserX = 0; // canvas coords
  private float mEraserY = 0;
  private float mEraserR = 0;

  // set the eraser circle
  // x, y canvas coords
  void setEraser( float x, float y, float r )
  {
    mEraserX = x;
    mEraserY = y;
    mEraserR = r;
    hasEraser = true;
  }

  void endEraser() { hasEraser = false; }

  // called only if hasEraser is true
  private void drawEraser( Canvas canvas )
  {
    Path path = new Path();
    path.addCircle( mEraserX, mEraserY, mEraserR, Path.Direction.CCW );
    // path.transform( mMatrix );
    canvas.drawPath( path, BrushManager.highlightPaint2 );
  }

  /** 
   * @return result code:
   *    0  no erasing
   *    1  point erased
   *    2  line complete erase
   *    3  line start erase
   *    4  line end erase 
   *    5  line split
   *    6  area complete erase
   *    7  area point erase
   *
   * x    X scene
   * y    Y scene
   * zoom canvas display zoom
   */
  int eraseAt( float x, float y, float zoom, EraseCommand eraseCmd, int erase_mode, float erase_size ) 
  {
    SelectionSet sel = new SelectionSet();
    float erase_radius = TDSetting.mCloseCutoff + erase_size / zoom;
    mSelection.selectAt( sel, x, y, erase_radius, Drawing.FILTER_ALL, false, false, false );
    int ret = 0;
    if ( sel.size() > 0 ) {
      synchronized( mCurrentStack ) {
        for ( SelectionPoint pt : sel.mPoints ) {
          DrawingPath path = pt.mItem;
          if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_LINE ) {
              DrawingLinePath line = (DrawingLinePath)path;
              // ArrayList< LinePoint > points = line.mPoints;
              // int size = points.size();
              LinePoint first = line.mFirst;
              LinePoint last  = line.mLast;
              int size = line.size();
              if ( size <= 2 || ( size == 3 && pt.mPoint == first.mNext ) ) // 2-point line OR erase midpoint of a 3-point line 
              {
                ret = 2; 
                eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
                mCurrentStack.remove( path );
                synchronized( mSelection ) {
                  mSelection.removePath( path );
                }
              } 
              else if ( pt.mPoint == first ) // erase first point of the multi-point line (2016-05-14)
              {
                ret = 3;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(0);
                LinePoint lp = first;
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized( mSelection ) {
                  mSelection.removeLinePoint( line, lp ); // index = 0
                  // mSelection.mPoints.remove( pt );        // index = 1
                }
                line.retracePath();
              }
              else if ( pt.mPoint == first.mNext ) // erase second point of the multi-point line
              {
                ret = 3;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(0);
                LinePoint lp = first;
                doRemoveLinePoint( line, lp, null );
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized( mSelection ) {
                  mSelection.removeLinePoint( line, lp ); // index = 0
                  mSelection.mPoints.remove( pt );        // index = 1
                }
                line.retracePath();
              } 
              else if ( pt.mPoint == last.mPrev ) // erase second-to-last of multi-point line
              {
                ret = 4;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(size-1);
                LinePoint lp = last;
                doRemoveLinePoint( line, lp, null );
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized( mSelection ) {
                  mSelection.removeLinePoint( line, lp ); // size -1
                  mSelection.mPoints.remove( pt );        // size -2
                }
                line.retracePath();
              } else { // erase a point in the middle of multi-point line
                ret = 5;
                doSplitLine( line, pt.mPoint, eraseCmd );
                break; // IMPORTANT break the for-loop
              }
            }
          } else if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_AREA ) {
              DrawingAreaPath area = (DrawingAreaPath)path;
              if ( area.size() <= 3 ) {
                ret = 6;
                eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
                mCurrentStack.remove( path );
                synchronized( mSelection ) {
                  mSelection.removePath( path );
                }
              } else {
                ret = 7;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                doRemoveLinePoint( area, pt.mPoint, pt );
                area.retracePath();
              }
            }
          } else if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_POINT ) {
              ret = 1;
              eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
              mCurrentStack.remove( path );
              synchronized( mSelection ) {
                mSelection.removePath( path );
              }
            }
          }
        }
      }
    }
    // checkLines();
    return ret;
  }

  /* Split the line at the point lp
   * The erase command is updated with the removal of the original line and the insert
   * of the two new pieces
   // called from synchronized( CurrentStack ) context
   // called only by eraseAt
   */
  private void doSplitLine( DrawingLinePath line, LinePoint lp, EraseCommand eraseCmd )
  {
    DrawingLinePath line1 = new DrawingLinePath( line.mLineType );
    DrawingLinePath line2 = new DrawingLinePath( line.mLineType );
    if ( line.splitAt( lp, line1, line2, true ) ) {
      // Log.v("DistoX", "split " + line.size() + " ==> " + line1.size() + " " + line2.size() );
      // synchronized( mCurrentStack ) // not neceessary: called in synchronized context
      {
        eraseCmd.addAction( EraseAction.ERASE_REMOVE, line );
        mCurrentStack.remove( line );
        if ( line1.size() > 1 ) {
          eraseCmd.addAction( EraseAction.ERASE_INSERT, line1 );
          mCurrentStack.add( line1 );
        }
        if ( line2.size() > 1 ) {
          eraseCmd.addAction( EraseAction.ERASE_INSERT, line2 );
          mCurrentStack.add( line2 );
        }
      }
      synchronized( mSelection ) {
        mSelection.removePath( line ); 
        if ( line1.size() > 1 ) mSelection.insertLinePath( line1 );
        if ( line2.size() > 1 ) mSelection.insertLinePath( line2 );
      }
    } else {
      // FIXME 
      // TDLog.Error( "FAILED splitAt " + lp.x + " " + lp.y );
      // line.dump();
    }
    // checkLines();
  }

  void splitLine( DrawingLinePath line, LinePoint lp )
  {
    if ( lp == null ) {
      return;
    }
    // if ( lp == line.mPoints.get(0) ) 
    if ( lp == line.mFirst )
    {
      return; // cannot split at first point
    }
    // int size = line.mPoints.size();
    int size = line.size();
    if ( size == 2 ) {
      return;
    }
    // if ( lp == line.mPoints.get(size-1) ) 
    if ( lp == line.mLast ) 
    {
      return; // cannot split at last point
    }
    clearSelected();

    DrawingLinePath line1 = new DrawingLinePath( line.mLineType );
    DrawingLinePath line2 = new DrawingLinePath( line.mLineType );
    if ( line.splitAt( lp, line1, line2, false ) ) {
      synchronized( mCurrentStack ) {
        mCurrentStack.remove( line );
        mCurrentStack.add( line1 );
        mCurrentStack.add( line2 );
      }
      synchronized( mSelection ) {
        mSelection.removePath( line ); 
        mSelection.insertLinePath( line1 );
        mSelection.insertLinePath( line2 );
      }
    }
    // checkLines();
  }

  // called from synchronized( mCurrentStack )
  private void doRemoveLinePoint( DrawingPointLinePath line, LinePoint point, SelectionPoint sp )
  {
    // int size = line.mPoints.size();
    // for ( int k=0; k<size; ++k ) {
    //   LinePoint lp = line.mPoints.get( k );
    //   if ( lp == point ) {
    //     line.mPoints.remove( k );
    //     mSelection.mPoints.remove( sp );
    //     return;
    //   }
    // }

    //line.mPoints.remove( point );
    line.remove( point );
    if ( sp != null ) { // sp can be null 
      synchronized( mSelection ) {
        mSelection.removePoint( sp );
      }
    }
    // checkLines();
  }

  boolean removeLinePoint( DrawingPointLinePath line, LinePoint point, SelectionPoint sp )
  {
    if ( point == null ) return false;
    // int size = line.mPoints.size();
    int size = line.size();
    if ( size <= 2 ) return false;
    synchronized( mSelection ) { clearSelected(); }
    // for ( int k=0; k<size; ++k ) 
    for ( LinePoint lp = line.mFirst; lp != null; lp = lp.mNext ) 
    {
      // LinePoint lp = line.mPoints.get( k );
      if ( lp == point ) {
        synchronized( mCurrentStack ) {
          // line.mPoints.remove( k );
          line.remove( point );
          synchronized( mSelection ) {
            mSelection.removePoint( sp );
          }
          // checkLines();
          return true;
        }
      }
    }
    // checkLines();
    return false;
  }

  private void doDeletePath( DrawingPath path )
  {
    synchronized( mCurrentStack ) {
      mCurrentStack.remove( path );
    }
    synchronized( mSelection ) {
      mSelection.removePath( path );
      clearSelected();
    }
  }

  // p is the path of sp
  void deleteSplay( DrawingPath p, SelectionPoint sp )
  {
    synchronized( mSplaysStack ) {
      mSplaysStack.remove( p );
    }
    synchronized( mSelection ) {
      mSelection.removePoint( sp );
      clearSelected();
    }
  }

  void deletePath( DrawingPath path, EraseCommand eraseCmd ) // called by DrawingSurface
  {
    doDeletePath( path );
    // checkLines();
    if ( eraseCmd != null ) eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
  }

  void deleteSectionLine( DrawingPath line, String scrap, EraseCommand cmd )
  {
    synchronized( mCurrentStack ) {
      int index = BrushManager.mPointLib.mPointSectionIndex;
      if ( index >= 0 ) {
        ArrayList<DrawingPath> todo = new ArrayList<DrawingPath>();
        final Iterator i = mCurrentStack.iterator();
        while ( i.hasNext() ){
          final ICanvasCommand c = (ICanvasCommand) i.next();
          if ( c.commandType() == 0 ) {
            DrawingPath p = (DrawingPath)c;
            if ( p.mType == DrawingPath.DRAWING_PATH_POINT ) {
              DrawingPointPath pt = (DrawingPointPath)p;
              if ( pt.mPointType == index && scrap.equals( pt.getOption( "-scrap" ) ) ) {
                todo.add(p);
              }
            }
          }
        }
        for ( DrawingPath pp : todo ) deletePath( pp, cmd );
      }
      deletePath( line, cmd );
    }
  }

  void sharpenLine( DrawingLinePath line ) 
  {
    synchronized( mCurrentStack ) {
      line.makeSharp( );
    }
    // checkLines();
  }

  void reduceLine( DrawingLinePath line ) 
  {
    synchronized( mSelection ) {
      mSelection.removePath( line );
      clearSelected();
    }
    synchronized( mCurrentStack ) {
      line.makeReduce( );
    }
    synchronized( mSelection ) {
      mSelection.insertPath( line );
    }
    // checkLines();
  }

  void closeLine( DrawingLinePath line )
  {
    synchronized( mCurrentStack ) {
      SelectionPoint sp = mSelection.getSelectionPoint( line.mLast );
      line.makeClose( );
      // rebucket last line point
      mSelection.rebucket( sp );
    }
  }

  // ooooooooooooooooooooooooooooooooooooooooooooooooooooo

  public void setDisplayMode( int mode ) { mDisplayMode = mode; }
  public int getDisplayMode( ) { return mDisplayMode; }

  // void setBounds( float x1, float x2, float y1, float y2 )
  // {
  //   mSelection = new Selection();
  // }
  //   try {
  //     mSelection = new Selection( x1, x2, y1, y2, 5.0f );
  //     mSelected  = new SelectionSet();
  //   } catch ( SelectionException e ) {
  //     TDLog.Error( "oversize: unable to select " );
  //     mSelection = null;
  //   }
  // } 

  // FIXME LEGS_SPLAYS
  void resetFixedPaint( Paint paint )
  {
    if( mLegsStack != null ) { 
      synchronized( mLegsStack ) {
        final Iterator i = mLegsStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath path = (DrawingPath) i.next();
          if ( path.mBlock == null || ( ! path.mBlock.mMultiBad ) ) {
            path.setPaint( paint );
          }
        }
      }
    }
    if( mSplaysStack != null ) { 
      synchronized( mSplaysStack ) {
        final Iterator i = mSplaysStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath path = (DrawingPath) i.next();
          if ( path.mBlock == null || ( ! path.mBlock.mMultiBad ) ) {
            path.setPaint( paint );
          }
        }
      }
    }
  }

  public void addLegPath( DrawingPath path, boolean selectable )
  { 
    if ( mLegsStack == null ) return;
    synchronized( mLegsStack ) {
      mLegsStack.add( path );
      if ( selectable ) {
        synchronized( mSelection ) {
          if ( path.mBlock != null ) {
            // Log.v( "DistoX", "selection add fixed path " + path.mBlock.mFrom + " " + path.mBlock.mTo );
          }
          mSelection.insertPath( path );
        }
      }
    }
  }  

  public void addSplayPath( DrawingPath path, boolean selectable )
  {
    if ( mSplaysStack == null ) return;
    synchronized( mSplaysStack ) {
      mSplaysStack.add( path );
      if ( selectable ) {
        synchronized( mSelection ) {
          if ( path.mBlock != null ) {
            // Log.v( "DistoX", "selection add fixed path " + path.mBlock.mFrom + " " + path.mBlock.mTo );
          }
          mSelection.insertPath( path );
        }
      }
    }
  }  
  
  public void setNorth( DrawingPath path )
  {
    mNorthLine = path;
  }
  
  public void addGrid( DrawingPath path, int k ) 
  { 
    if ( mGridStack1 == null ) return;
    synchronized( mGridStack1 ) {
      switch (k) {
        case 1:   mGridStack1.add( path );   break;
        case 10:  mGridStack10.add( path );  break;
        case 100: mGridStack100.add( path ); break;
      }
    }
  }

  // void setScaleBar( float x0, float y0 ) 
  // {
  //   if ( mCurrentStack.size() > 0 ) return;
  //   DrawingLinePath scale_bar = new DrawingLinePath( BrushManager.mLineLib.mLineSectionIndex );
  //   scale_bar.addStartPoint( x0 - 50, y0 );
  //   scale_bar.addPoint( x0 + 50, y0 );  // 5 meters
  //   synchronized( mCurrentStack ) {
  //     mCurrentStack.add( scale_bar );
  //   }
  // }

  DrawingStationPath getUserStation( String name )
  {
    if ( name != null ) {
      for ( DrawingStationPath p : mUserStations ) if ( name.equals( p.name() ) ) return p;
    }
    return null;
  }

  void removeUserStation( DrawingStationPath path )
  {
    // Log.v("DistoX", "remove user station " + path.mName );
    synchronized( mUserStations ) {
      mUserStations.remove( path );
    }
  }

  // boolean hasUserStation( String name )
  // {
  //   for ( DrawingStationPath p : mUserStations ) if ( p.mName.equals( name ) ) return true;
  //   return false;
  // }
  

  void addUserStation( DrawingStationPath path )
  {
    // Log.v("DistoX", "add user station " + path.mName );
    synchronized( mUserStations ) {
      mUserStations.add( (DrawingStationPath)path );
    }
  }

  void addCommand( DrawingPath path )
  {
    // TDLog.Log( TDLog.LOG_PLOT, "addCommand stack size  " + mCurrentStack.size() );
    // TDLog.Log( TDLog.LOG_PLOT, "addCommand path " + path.toString() );
    // Log.v("DistoX", "add command type " + path.mType + " " + path.left + " " + path.top + " " 
    //        + mBBox.left + " " + mBBox.top + " " + mBBox.right + " " + mBBox.bottom );

    mRedoStack.clear();

    if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
      DrawingAreaPath area = (DrawingAreaPath)path;
      if ( area.mAreaCnt > mMaxAreaIndex ) {
        mMaxAreaIndex = area.mAreaCnt;
      }
    }

    // if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
    //   DrawingLinePath line = (DrawingLinePath)path;
    //   LinePoint lp = line.mFirst;
    //   Log.v("PTDistoX", "add path. size " + line.size() + " start " + lp.x + " " + lp.y );
    // }
    
    synchronized( mCurrentStack ) {
      mCurrentStack.add( path );
    }
    synchronized( mSelection ) {
      mSelection.insertPath( path );
    }
    
    // checkLines();
  }

  public void addScrapOutlinePath( DrawingLinePath path ) 
  {
    synchronized( mScrap ) {
      mScrap.add( path );
    }
  }

  public void deleteSectionPoint( String scrap_name, EraseCommand cmd )
  {
    int index = BrushManager.mPointLib.mPointSectionIndex;
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand icc : mCurrentStack ) { // FIXME reverse_iterator
        if ( icc.commandType() == 0 ) { // DrawingPath
          DrawingPath path = (DrawingPath)icc;
          if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            DrawingPointPath dpp = (DrawingPointPath) path;
            if ( dpp.mPointType == index ) {
              String vals[] = dpp.mOptions.split(" ");
              int len = vals.length;
              for ( int k = 0; k < len; ++k ) {
                if ( scrap_name.equals( vals[k] ) ) {
                  deletePath( path, cmd );
                  return;
                }
              }
            }
          }
        }
      }
    }
  }

  // called by DrawingSurface.getBitmap()
  public RectF getBitmapBounds()
  {
    RectF bounds = new RectF();
    RectF b = new RectF();
    if( mSplaysStack != null ) { 
      synchronized( mSplaysStack ) {
        final Iterator i = mSplaysStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i.next();
          drawingPath.mPath.computeBounds( b, true );
          bounds.union( b );
        }
      }
    }
    if( mLegsStack != null ) { 
      synchronized( mLegsStack ) {
        final Iterator i = mLegsStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i.next();
          drawingPath.mPath.computeBounds( b, true );
          bounds.union( b );
        }
      }
    }

    if( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        final Iterator i = mCurrentStack.iterator();
        while ( i.hasNext() ){
          final ICanvasCommand cmd = (ICanvasCommand) i.next();
          cmd.computeBounds( b, true );
          bounds.union( b );
        }
      }
    }
    return bounds;
  }

  private float mBitmapScale = 1;

  // returns the last used bitmap scale
  float getBitmapScale() { return mBitmapScale; }

  public Bitmap getBitmap()
  {
    RectF bounds = getBitmapBounds();
    // TDLog.Log(  TDLog.LOG_PLOT, "getBitmap Bounds " + bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom );
    mBitmapScale = TDSetting.mBitmapScale;

    int width  = (int)((bounds.right - bounds.left + 2 * BORDER) );
    int height = (int)((bounds.bottom - bounds.top + 2 * BORDER) );
    int max = (int)( 8 * 1024 * 1024 / (mBitmapScale * mBitmapScale) );  // 16 MB 2 B/pixel
    while ( width*height > max ) {
      mBitmapScale /= 2;
      max *= 4;
    }
    width  = (int)((bounds.right - bounds.left + 2 * BORDER) * mBitmapScale );
    height = (int)((bounds.bottom - bounds.top + 2 * BORDER) * mBitmapScale );
   
    Bitmap bitmap = null;
    while ( bitmap == null && mBitmapScale > 0.05 ) {
      try {
        // bitmap =  Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);
        bitmap =  Bitmap.createBitmap (width, height, Bitmap.Config.RGB_565);
      } catch ( OutOfMemoryError e ) {
        mBitmapScale /= 2;
        width  = (int)((bounds.right - bounds.left + 2 * BORDER) * mBitmapScale );
        height = (int)((bounds.bottom - bounds.top + 2 * BORDER) * mBitmapScale );
      } catch ( IllegalArgumentException e ) {
        TDLog.Error("create bitmap illegal arg " + e.getMessage() );
        return null;
      }
    }
    if ( mBitmapScale <= 0.05 ) return null;
    if ( bitmap == null ) return null;
    // Log.v( "DistoX", "PNG mBitmapScale " + mBitmapScale + "/" + TDSetting.mBitmapScale + " " + width + "x" + height );
    Canvas c = new Canvas (bitmap);
    // c.drawColor(TDSetting.mBitmapBgcolor, PorterDuff.Mode.CLEAR);
    c.drawColor( TDSetting.mBitmapBgcolor );

    // commandManager.execute All(c,previewDoneHandler);
    // previewPath.draw(c);
    c.drawBitmap (bitmap, 0, 0, null);

    Matrix mat = new Matrix();
    float sca = 1 / mBitmapScale;
    mat.postTranslate( BORDER - bounds.left, BORDER - bounds.top );
    mat.postScale( mBitmapScale, mBitmapScale );
    if ( mGridStack1 != null ) {
      synchronized( mGridStack1 ) {
        final Iterator i1 = mGridStack1.iterator();
        while ( i1.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i1.next();
          drawingPath.draw( c, mat, sca, null );
        }
        final Iterator i10 = mGridStack10.iterator();
        while ( i10.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i10.next();
          drawingPath.draw( c, mat, sca, null );
        }
        final Iterator i100 = mGridStack100.iterator();
        while ( i100.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i100.next();
          drawingPath.draw( c, mat, sca, null );
        }
        if ( mNorthLine != null ) mNorthLine.draw( c, mat, sca, null );
      }
    }
    if ( mSplaysStack != null ) {
      synchronized( mSplaysStack ) {
        final Iterator i = mSplaysStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i.next();
          drawingPath.draw( c, mat, sca, null );
        }
      }
    }
    if ( mLegsStack != null ) {
      synchronized( mLegsStack ) {
        final Iterator i = mLegsStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i.next();
          drawingPath.draw( c, mat, sca, null );
        }
      }
    }
 
    if ( mStations != null ) {  
      synchronized( mStations ) {
        for ( DrawingStationName st : mStations ) {
          st.draw( c, mat, sca, null );
        }
      }
    }

    if( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        final Iterator i = mCurrentStack.iterator();
        while ( i.hasNext() ){
          final ICanvasCommand cmd = (ICanvasCommand) i.next();
          cmd.draw( c, mat, sca, null );
        }
      }
    }
    // checkLines();
    return bitmap;
  }

  static final String actionName[] = { "remove", "insert", "modify" };

  public void undo ()
  {

    final int length = currentStackLength();
    if ( length > 0) {
      final ICanvasCommand cmd = mCurrentStack.get(  length - 1  );

      synchronized( mCurrentStack ) {
        mCurrentStack.remove( length - 1 );
        // cmd.undoCommand();
      }
      mRedoStack.add( cmd );

      if ( cmd.commandType() == 0 ) {
        synchronized( mSelection ) {
          mSelection.removePath( (DrawingPath)cmd );
        }
      } else { // EraseCommand
        EraseCommand eraseCmd = (EraseCommand)cmd;
        int na = eraseCmd.mActions.size(); 
        while ( na > 0 ) {
          --na;
          EraseAction action = eraseCmd.mActions.get( na );
          DrawingPath path = action.mPath;
          // Log.v("DistoX", "UNDO " + actionName[action.mType] + " path " + path.toString() );
          if ( action.mInitialType == EraseAction.ERASE_INSERT ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.remove( path );
            }
            synchronized( mSelection ) {
              mSelection.removePath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_REMOVE ) {
            synchronized( mCurrentStack ) {
              action.restorePoints( true ); // true: use old points
              mCurrentStack.add( path );
            }
            synchronized( mSelection ) {
              mSelection.insertPath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_MODIFY ) { // undo modify
            synchronized( mSelection ) {
              mSelection.removePath( path );
            }
            synchronized( mCurrentStack ) {
              action.restorePoints( true );
            }
            synchronized( mSelection ) {
              mSelection.insertPath( path );
            }
          }
        }
      }
    }
    // checkLines();
  }

  public int currentStackLength()
  {
    final int length = mCurrentStack.toArray().length;
    return length;
  }

  // line points are scene-coords
  // continuation is checked in canvas-coords: canvas = offset + scene * zoom
  DrawingLinePath getLineToContinue( LinePoint lp, int type, float zoom, float size )
  {
    String group = BrushManager.getLineGroup( type );
    if ( group == null ) return null;

    float delta = 2 * size / zoom;

    DrawingLinePath ret = null;
    synchronized( mCurrentStack ) {
      final Iterator i = mCurrentStack.iterator();
      while ( i.hasNext() ){
        ICanvasCommand cmd = (ICanvasCommand) i.next();
        if ( cmd.commandType() != 0 ) continue; // FIXME EraseCommand

        final DrawingPath drawingPath = (DrawingPath)cmd;
        if ( drawingPath.mType == DrawingPath.DRAWING_PATH_LINE ) {
          DrawingLinePath linePath = (DrawingLinePath)drawingPath;
          // if ( linePath.mLineType == type ) 
          if ( group.equals( BrushManager.getLineGroup( linePath.mLineType ) ) )
          {
            if ( linePath.mFirst.distance( lp ) < delta || linePath.mLast.distance( lp ) < delta ) {
              if ( ret != null ) return null; // ambiguity
              ret = linePath;
            }
          }
        }
      }
    }
    // if ( ret != null ) mSelection.removePath( ret ); // FIXME do not remove continuation line
    // checkLines();
    return ret;
  }
        
  /** add the points of the first line to the second line
   */
  void addLineToLine( DrawingLinePath line, DrawingLinePath line0 )
  {
    synchronized( mSelection ) {
      mSelection.removePath( line0 );
    }
    synchronized( mCurrentStack ) {
      boolean reverse = line0.mFirst.distance( line.mFirst ) < line0.mLast.distance( line.mFirst );
      if ( reverse ) line0.reversePath();
      line0.append( line );
      if ( reverse ) {
        line0.reversePath();
        line0.computeUnitNormal();
      }
    }
    synchronized( mSelection ) {
      mSelection.insertPath( line0 );
    }
    // checkLines();
  }

  private boolean showStationSplays( DrawingPath p, ArrayList<String> splay_stations ) 
  {
    DBlock blk = p.mBlock;
    if ( blk == null ) return false;
    String station = blk.mFrom;
    if ( station == null || station.length() == 0 ) return false;
    return splay_stations.contains( station );
  }

  // N.B. doneHandler is not used
  public void executeAll( Canvas canvas, float zoom, ArrayList<String> splay_stations )
  {
    if ( canvas == null ) {
      TDLog.Error( "drawing executeAll: null canvas");
      return;
    }

    boolean legs     = (mDisplayMode & DisplayMode.DISPLAY_LEG     ) != 0;
    boolean splays   = (mDisplayMode & DisplayMode.DISPLAY_SPLAY   ) != 0;
    boolean stations = (mDisplayMode & DisplayMode.DISPLAY_STATION ) != 0;
    boolean grids    = (mDisplayMode & DisplayMode.DISPLAY_GRID    ) != 0;
    boolean outline  = (mDisplayMode & DisplayMode.DISPLAY_OUTLINE ) != 0;
    boolean scaleRef = (mDisplayMode & DisplayMode.DISPLAY_SCALE_REF ) != 0;

    boolean spoints   = false;
    boolean slines    = false;
    boolean sareas    = false;
    boolean sshots    = false;
    boolean sstations = false;

    switch (mSelectMode) {
      case Drawing.FILTER_ALL:
        sshots = true;
        sstations = stations;
        spoints = slines = sareas = true;
        break;
      case Drawing.FILTER_POINT:
        spoints = true;
        break;
      case Drawing.FILTER_LINE:
        slines = true;
        break;
      case Drawing.FILTER_AREA:
        sareas = true;
        break;
      case Drawing.FILTER_SHOT:
        sshots = true;
        break;
      case Drawing.FILTER_STATION:
        sstations = true;
        break;
    }

    if( grids && mGridStack1 != null ) {
      synchronized( mGridStack1 ) {
        if ( mScale < 1 ) {
          final Iterator i1 = mGridStack1.iterator();
          while ( i1.hasNext() ){
            final DrawingPath drawingPath = (DrawingPath) i1.next();
            drawingPath.draw( canvas, mMatrix, mScale, mBBox );
          }
        }
        if ( mScale < 10 ) {
          final Iterator i10 = mGridStack10.iterator();
          while ( i10.hasNext() ){
            final DrawingPath drawingPath = (DrawingPath) i10.next();
            drawingPath.draw( canvas, mMatrix, mScale, mBBox );
          }
        }
        final Iterator i100 = mGridStack100.iterator();
        while ( i100.hasNext() ){
          final DrawingPath drawingPath = (DrawingPath) i100.next();
          drawingPath.draw( canvas, mMatrix, mScale, mBBox );
        }
        if ( mNorthLine != null ) mNorthLine.draw( canvas, mMatrix, mScale, mBBox );
        if(scaleRef && (mScaleRef != null)) {
          mScaleRef.draw(canvas, zoom);
        }
      }
    }

    if ( legs && mLegsStack != null ) {
      synchronized( mLegsStack ) {
        final Iterator i = mLegsStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath path = (DrawingPath) i.next();
          path.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mSplaysStack != null && ( splays || splay_stations.size() > 0 ) ) {
      synchronized( mSplaysStack ) {
        final Iterator i = mSplaysStack.iterator();
        while ( i.hasNext() ){
          final DrawingPath path = (DrawingPath) i.next();
          if ( splays || showStationSplays( path, splay_stations ) ) {
            path.draw( canvas, mMatrix, mScale, mBBox );
          }
        }
      }
    }
    if ( mScrap != null && mScrap.size() > 0 ) {
      synchronized( mScrap )  {
        final Iterator i = mScrap.iterator();
        while ( i.hasNext() ){
          final DrawingLinePath path = (DrawingLinePath) i.next();
          path.draw( canvas, mMatrix, mScale, null /* mBBox */ );
        }
      }
    }
 
    if ( stations && mStations != null ) {  
      synchronized( mStations ) {
        for ( DrawingStationName st : mStations ) {
          st.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        if ( outline ) {
          final Iterator i = mCurrentStack.iterator();
          while ( i.hasNext() ){
            final ICanvasCommand cmd = (ICanvasCommand) i.next();
            if ( cmd.commandType() == 0 ) {
              DrawingPath path = (DrawingPath)cmd;
              if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
                DrawingLinePath line = (DrawingLinePath)path;
                if ( line.hasOutline() ) {
                  cmd.draw( canvas, mMatrix, mScale, mBBox );
                }
              }
            }
          }
        } else {
          final Iterator i = mCurrentStack.iterator();
          while ( i.hasNext() ){
            final ICanvasCommand cmd = (ICanvasCommand) i.next();
            if ( cmd.commandType() == 0 ) {
              cmd.draw( canvas, mMatrix, mScale, mBBox );
              DrawingPath path = (DrawingPath)cmd;
              if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
                DrawingLinePath line = (DrawingLinePath)path;
                if ( line.mLineType == BrushManager.mLineLib.mLineSectionIndex ) { // add tick to section-lines
                  LinePoint lp = line.mFirst;
                  Path path1 = new Path();
                  path1.moveTo( lp.x, lp.y );
                  path1.lineTo( lp.x+line.mDx*10, lp.y+line.mDy*10 );
                  path1.transform( mMatrix );
                  canvas.drawPath( path1, BrushManager.mStationSymbol.mPaint );
                }
              }
            }
          }
        }
      }
    }
    if ( ! TDSetting.mAutoStations ) {
      synchronized( mUserStations ) {
        for ( DrawingStationPath p : mUserStations ) {
          p.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mDisplayPoints ) {
      synchronized( mSelection ) {
        float radius = TDSetting.mDotRadius/zoom;
        final Iterator i = mSelection.mBuckets.iterator();
        while ( i.hasNext() ) {
          final SelectionBucket bucket = (SelectionBucket) i.next();
          if ( bucket.intersects( mBBox ) ) {
            for ( SelectionPoint pt : bucket.mPoints ) { 
              int type = pt.type();
              if ( ( type == DrawingPath.DRAWING_PATH_POINT && ! spoints ) 
                || ( type == DrawingPath.DRAWING_PATH_LINE && ! slines ) 
                || ( type == DrawingPath.DRAWING_PATH_AREA && ! sareas ) 
                || ( type == DrawingPath.DRAWING_PATH_FIXED && ! (legs && sshots) )
                || ( type == DrawingPath.DRAWING_PATH_SPLAY && ! (splays && sshots) )
                || ( type == DrawingPath.DRAWING_PATH_NAME  && ! (sstations) ) ) continue;
              float x, y;
              if ( pt.mPoint != null ) { // line-point
                x = pt.mPoint.x;
                y = pt.mPoint.y;
              } else {  
                x = pt.mItem.cx;
                y = pt.mItem.cy;
              }
              Path path = new Path();
              path.addCircle( x, y, radius, Path.Direction.CCW );
              path.transform( mMatrix );
              canvas.drawPath( path, BrushManager.highlightPaint2 );
            }
          }
        }
        // for ( SelectionPoint pt : mSelection.mPoints ) { // FIXME SELECTION
        //   float x, y;
        //   if ( pt.mPoint != null ) { // line-point
        //     x = pt.mPoint.x;
        //     y = pt.mPoint.y;
        //   } else {  
        //     x = pt.mItem.cx;
        //     y = pt.mItem.cy;
        //   }
        //   Path path = new Path();
        //   path.addCircle( x, y, radius, Path.Direction.CCW );
        //   path.transform( mMatrix );
        //   canvas.drawPath( path, BrushManager.highlightPaint2 );
        // }

      }
      synchronized( mSelected ) {
        if ( mSelected.mPoints.size() > 0 ) { // FIXME SELECTIOM
          float radius = 4*TDSetting.mDotRadius/zoom;
          Path path;
          SelectionPoint sp = mSelected.mHotItem;
          if ( sp != null ) {
            float x, y;
            LinePoint lp = sp.mPoint;
            DrawingPath item = sp.mItem;
            LinePoint lp1 = sp.mLP1;
            LinePoint lp2 = sp.mLP2;

            if ( lp != null ) { // line-point
              x = lp.x;
              y = lp.y;
            } else {
              x = item.cx;
              y = item.cy;
            }
            path = new Path();
            path.addCircle( x, y, radius, Path.Direction.CCW );
            path.transform( mMatrix );
            canvas.drawPath( path, BrushManager.highlightPaint2 );
            if ( lp != null && lp.has_cp ) {
              path = new Path();
              path.moveTo( lp.x1, lp.y1 );
              path.lineTo( lp.x2, lp.y2 );
              path.lineTo( x, y );
              path.addCircle( lp.x1, lp.y1, radius/2, Path.Direction.CCW );
              path.addCircle( lp.x2, lp.y2, radius/2, Path.Direction.CCW );
              path.transform( mMatrix );
              canvas.drawPath( path, BrushManager.highlightPaint3 );
            }
            if ( item.mType == DrawingPath.DRAWING_PATH_LINE ) {
              Paint paint = BrushManager.fixedYellowPaint;
              DrawingLinePath line = (DrawingLinePath) item;
              lp = line.mFirst;
              LinePoint lpn = lp1;
              if ( lp == lp1 ) {
                paint = BrushManager.fixedOrangePaint;
                lpn = lp2;
              }
              path = new Path();
              path.moveTo( lp.x+line.mDx*10, lp.y+line.mDy*10 );
              path.lineTo( lp.x, lp.y );
              for ( lp = lp.mNext; lp != lpn && lp != null; lp = lp.mNext ) {
                if ( lp.has_cp ) {
                  path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                } else {
                  path.lineTo( lp.x, lp.y );
                }
              }
              path.transform( mMatrix );
              canvas.drawPath( path, paint );
              if ( lp != lp2 ) {
                path = new Path();
                path.moveTo( lp.x, lp.y );
                for ( lp = lp.mNext; lp != lp2 && lp != null; lp = lp.mNext ) {
                  if ( lp.has_cp ) {
                    path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                  } else {
                    path.lineTo( lp.x, lp.y );
                  }
                }
                path.transform( mMatrix );
                canvas.drawPath( path, BrushManager.fixedOrangePaint );
              }
              if ( lp != null && lp.mNext != null ) {
                path = new Path();
                path.moveTo( lp.x, lp.y );
                for ( lp = lp.mNext; lp != null; lp = lp.mNext ) {
                  if ( lp.has_cp ) {
                    path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                  } else {
                    path.lineTo( lp.x, lp.y );
                  }
                }
                path.transform( mMatrix );
                canvas.drawPath( path, BrushManager.fixedYellowPaint );
              }
            }
          }
          radius = radius/3; // 2/zoom;
          for ( SelectionPoint pt : mSelected.mPoints ) {
            float x, y;
            if ( pt.mPoint != null ) { // line-point
              x = pt.mPoint.x;
              y = pt.mPoint.y;
            } else {
              x = pt.mItem.cx;
              y = pt.mItem.cy;
            }
            path = new Path();
            path.addCircle( x, y, radius, Path.Direction.CCW );
            path.transform( mMatrix );
            canvas.drawPath( path, BrushManager.highlightPaint );
          }
        }
      } 
    }
    synchronized( mGridStack1 ) {
      if ( mFirstReference != null )  mFirstReference.draw( canvas, mMatrix, mScale, null );
      if ( mSecondReference != null ) mSecondReference.draw( canvas, mMatrix, mScale, null );
    }

    if ( hasEraser ) {
      drawEraser( canvas );
    }
  }

  // boolean hasStationName( String name )
  // {
  //   if ( name == null ) return false;
  //   synchronized( mCurrentStack ) {
  //     final Iterator i = mCurrentStack.iterator();
  //     while ( i.hasNext() ){
  //       final ICanvasCommand cmd = (ICanvasCommand) i.next();
  //       if ( cmd.commandType() == 0 ) {
  //         DrawingPath p = (DrawingPath) cmd;
  //         if ( p.mType == DrawingPath.DRAWING_PATH_STATION ) {
  //           DrawingStationPath sp = (DrawingStationPath)p;
  //           if ( name.equals( sp.mName ) ) return true;
  //         }
  //       }
  //     }
  //   }
  //   return false;
  // }

  public boolean hasMoreRedo()
  {
    return  mRedoStack.toArray().length > 0;
  }

  public boolean hasMoreUndo()
  {
    return  mCurrentStack.toArray().length > 0;
  }

  public void redo()
  {
    final int length = mRedoStack.toArray().length;
    if ( length > 0) {
      final ICanvasCommand cmd = mRedoStack.get(  length - 1  );
      mRedoStack.remove( length - 1 );

      if ( cmd.commandType() == 0 ) {
        DrawingPath redoCommand = (DrawingPath)cmd;
        synchronized( mCurrentStack ) {
          mCurrentStack.add( redoCommand );
        }
        synchronized( mSelection ) {
          mSelection.insertPath( redoCommand );
        }
      } else {
        EraseCommand eraseCmd = (EraseCommand) cmd;
        for ( EraseAction action : eraseCmd.mActions ) {
          DrawingPath path = action.mPath;
          // Log.v("DistoX", "REDO " + actionName[action.mType] + " path " + path.mType );
          if ( action.mInitialType == EraseAction.ERASE_INSERT ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.add( path );
            }
            synchronized( mSelection ) {
              mSelection.insertPath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_REMOVE ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.remove( path );
            }
            synchronized( mSelection ) {
              mSelection.removePath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_MODIFY ) {
            synchronized( mSelection ) {
              mSelection.removePath( path );
            }
            synchronized( mCurrentStack ) {
              action.restorePoints( false ); // false: use new points
            }
            synchronized( mSelection ) {
              mSelection.insertPath( path );
            }
          }
        }
        synchronized( mCurrentStack ) {
          mCurrentStack.add( cmd );
        }
      }
    }
    // checkLines();
  }

  boolean setRangeAt( float x, float y, float zoom, int type, float size )
  {
    SelectionPoint sp1 = mSelected.mHotItem;
    if ( sp1 == null ) {
      // Log.v("DistoX", "set range at: hotItem is null" );
      return false;
    }
    DrawingPath item = sp1.mItem;
    if ( item.mType != DrawingPath.DRAWING_PATH_LINE && item.mType != DrawingPath.DRAWING_PATH_AREA ) {
      // Log.v("DistoX", "set range at: item not line/area" );
      // mSelected.clear();
      return false;
    }
    float radius = TDSetting.mCloseCutoff + size / zoom;
    SelectionPoint sp2 = null;
    synchronized ( mSelected ) {
      sp2 = mSelection.selectOnItemAt( item, x, y, 4*radius );
    }
    if ( sp2 == null ) {
      // Log.v("DistoX", "set range at: select on Item return null");
      mSelected.clear();
      return false;
    }
    // range is sp1 -- sp2
    LinePoint lp1 = sp1.mPoint;
    LinePoint lp2 = sp2.mPoint;
    int cnt = 0;
    LinePoint lp = lp1;
    for ( ; lp != null; lp=lp.mNext ) { ++cnt; if ( lp == lp2 ) break; }
    if ( lp == null ) {
      cnt = 0;
      for ( lp=lp1; lp != null; lp=lp.mPrev ) { ++cnt; if ( lp == lp2 ) break; }
      if ( lp == null ) { // error
        // Log.v("DistoX", "set range at: error lp==null");
        return false;
      }
      lp = lp1; lp1 = lp2; lp2 = lp; // swap lp1 <--> lp2
    } 
    LinePoint lp0 = lp1;
    float d1 = 0;
    float d2 = 0;
    int c1 = 0;
    int c2 = 0;
    for ( int c = cnt/2; c > 0; --c ) {
      ++ c1;
      lp = lp0.mNext; 
      d1 += lp0.distance( lp );
      lp0 = lp;
    }
    LinePoint lp4 = lp0;
    for ( LinePoint lp3 = lp0.mNext; lp3 != lp2 && lp3 != null; lp3=lp3.mNext) {
      ++ c2;
      d2 += lp4.distance( lp3 );
      lp4 = lp3;
    }
    // Log.v("DistoX", "set range d1 " + d1 + " d2 " + d2 + " C " + cnt + " " + c1 + " " + c2 );
     
    // now make the range sp1 -- sp2 and the hotItem the midpoint
    SelectionPoint sp = mSelection.getSelectionPoint( lp0 ); 
    sp.mLP1 = lp1;
    sp.mLP2 = lp2;
    sp.mD1 = d1;
    sp.mD2 = d2;
    sp.mRangeType = type;

    mSelected.clear();
    mSelected.addPoint( sp );
    mSelected.mHotItem = sp;

    return true;
  }

    
  public SelectionSet getItemsAt( float x, float y, float zoom, int mode, float size )
  {
    float radius = TDSetting.mCloseCutoff + size/zoom; // TDSetting.mSelectness / zoom;
    // Log.v( "DistoX", "getItemAt " + x + " " + y + " zoom " + zoom + " mode " + mode + " size " + size + " " + radius );
    boolean legs   = (mDisplayMode & DisplayMode.DISPLAY_LEG) != 0;
    boolean splays = (mDisplayMode & DisplayMode.DISPLAY_SPLAY ) != 0;
    boolean stations = (mDisplayMode & DisplayMode.DISPLAY_STATION ) != 0;
    synchronized ( mSelected ) {
      mSelected.clear();
      mSelection.selectAt( mSelected, x, y, radius, mode, legs, splays, stations );
      if ( mSelected.mPoints.size() > 0 ) {
        // Log.v("DistoX", "seleceted " + mSelected.mPoints.size() + " points " );
        mSelected.nextHotItem();
      }
    }
    return mSelected;
  }

  void splitHotItem()
  { 
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE && sp.type() != DrawingPath.DRAWING_PATH_AREA ) return;
    LinePoint lp = sp.mPoint;
    if ( lp == null ) return;
    float x = lp.x;
    float y = lp.y;
    DrawingPointLinePath line = (DrawingPointLinePath)sp.mItem;
    LinePoint p1 = line.insertPointAfter( x, y, lp );
    SelectionPoint sp1 = null;
    synchronized( mSelection ) {
      sp1 = mSelection.insertPathPoint( line, p1 );
    }
    if ( sp1 != null ) {
      synchronized( mSelected ) {
        mSelected.mPoints.add( sp1 );
      }
    }
  }

  private float project( LinePoint q, LinePoint p0, LinePoint p1 )
  {
    float x01 = p1.x - p0.x;
    float y01 = p1.y - p0.y;
    return ((q.x-p0.x)*x01 + (q.y-p0.y)*y01) / ( x01*x01 + y01*y01 );
  }
    
  private float distance( LinePoint q, LinePoint p0, LinePoint p1 )
  {
    float x01 = p1.x - p0.x;
    float y01 = p1.y - p0.y;
    return TDMath.abs( (q.x-p0.x)*y01 - (q.y-p0.y)*x01 ) / TDMath.sqrt( x01*x01 + y01*y01 );
  }
    
      
  boolean moveHotItemToNearestPoint()
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return false;
    float x = 0.0f;
    float y = 0.0f;
    if ( sp.type() == DrawingPath.DRAWING_PATH_POINT ) {
      x = sp.mItem.cx;
      y = sp.mItem.cy;
    } else if ( sp.type() == DrawingPath.DRAWING_PATH_LINE || sp.type() == DrawingPath.DRAWING_PATH_AREA ) {
      x = sp.mPoint.x;
      y = sp.mPoint.y;
    } else {
      return false;
    }
    SelectionPoint spmin = mSelection.getNearestPoint( sp, x, y, 10f );

    if ( spmin != null ) {
      if ( spmin.type() == DrawingPath.DRAWING_PATH_LINE || spmin.type() == DrawingPath.DRAWING_PATH_AREA ) {
        x = spmin.mPoint.x - x;
        y = spmin.mPoint.y - y;
      } else {
        x = spmin.mItem.cx - x;
        y = spmin.mItem.cy - y;
      }
      // sp.shiftBy( x, y, 0f );
      sp.shiftBy( x, y );
    }
    return true;
  }

  class NearbySplay
  {
    float dx, dy;
    float d; // distance from point
    LinePoint pt; // point
    float llen, rlen;

    NearbySplay( float xx, float yy, float dd, LinePoint lp )
    {
      dx = xx;
      dy = yy;
      d  = dd;
      pt = lp;
    }
  }
  
  // return 0 ok
  //       -1 no hot item
  //       -2 not line
  //       -3 no splay
  int snapHotItemToNearestSplays( float dthr )
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return -1;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE ) return -2;

    DrawingPath item = sp.mItem;
    DrawingLinePath line = (DrawingLinePath)item;

    // nearby splays are the splays that get close enough (dthr) to the line
    ArrayList< NearbySplay > splays = new ArrayList< NearbySplay >();
    for ( DrawingPath fxd : mSplaysStack ) {
      float x = fxd.x2;
      float y = fxd.y2;
      float dmin = dthr;
      LinePoint lpmin = null;
      for ( LinePoint lp2 = line.mFirst; lp2 != null; lp2=lp2.mNext ) {
        float d = lp2.distance( x, y );
        if ( d < dmin ) {
          dmin = d;
          lpmin = lp2;
        } else if ( lpmin != null ) { // if distances increase after a good min, break
          splays.add( new NearbySplay( fxd.x2 - lpmin.x, fxd.y2 - lpmin.y, dmin, lpmin ) );
          break;
        }
      }
    }
    // Log.v("DistoX", "Nearby splays " + splays.size() + " line size " + line.size() );
    int ks = splays.size();
    if ( ks == 0 ) return -3;
    // check that two splays do not have the same linepoint
    for ( int k1 = 0; k1 < ks; ) {
      NearbySplay nbs1 = splays.get( k1 );
      int dk1 = 1; // increment of k1
      int k2 = k1+1;
      while ( k2<ks ) {
        NearbySplay nbs2 = splays.get( k2 );
        if ( nbs1.pt == nbs2.pt ) {
          ks --;
          if ( nbs1.d <= nbs2.d ) {
            splays.remove( k2 );
          } else {
            splays.remove( k1 );
            dk1 = 0;
            break;
          }
        } else {
          k2 ++;
        }
      }
      k1 += dk1;
    }
    // Log.v("DistoX", "Nearby splays " + splays.size() + " / " + ks );

    // compute distances between consecutive line points
    // and order splays following the line path
    int k = 0; // partition of unity
    float len = 0.001f;
    LinePoint lp1 = line.mFirst;
    int size = line.size();
    float dist[ ] = new float[ size ]; 
    int k0 = 0;
    for ( LinePoint lp2 = line.mFirst; lp2 != null; lp2 = lp2.mNext ) {
      dist[k0] = lp1.distance( lp2 );
      len += dist[k0];
      ++k0;

      int kk = k;
      for ( ; kk<ks; ++kk ) {
        if ( lp2 == splays.get(kk).pt ) {
          if ( kk != k ) { // swap splays k <--> kk
            NearbySplay nbs = splays.remove( kk );
            splays.add( k, nbs );
          }
          splays.get(k).llen = len;
          if ( k > 0 ) splays.get( k-1 ).rlen = len;
          len = 0;
          ++ k;
          break;
        }
      }
      lp1 = lp2; // lp1 = previous point
    }
    len += 0.001f;
    splays.get( k-1 ).rlen = len;

    //   |----------*--------*-----
    //      llen   sp1 rlen
    //                 llen sp2 rlen

    k0 = 0;
    int kl = -1;
    int kr = 0;
    len = 0;
    LinePoint lp2 = line.mFirst;
    NearbySplay spr = null; // right splay
    for ( NearbySplay spl : splays ) { // left splay
      while ( lp2 != spl.pt /* && lp2 != null && k0 < size */ ) { // N.B. lp2 must be non-null and k0 must be < size
        len += dist[k0];
        float dx = len/spl.llen * spl.dx;
        float dy = len/spl.llen * spl.dy;
        if ( spr != null ) {
          dx += (1 - len/spr.rlen) * spr.dx;
          dy += (1 - len/spr.rlen) * spr.dy;
        }
        lp2.shiftBy( dx, dy );
        lp2 = lp2.mNext;
        ++ k0;
      }
      // if ( lp2 == spl.pt ) { // this must be true
        lp2.shiftBy( spl.dx, spl.dy );
        lp2 = lp2.mNext;
      // }
      spr = spl;
      // if ( k0 >= size ) break;
      ++ k0;
      len = 0;
    }
    while ( lp2 != null /* && k0 < size */ ) { // N.B. k0 must be < size
      len += dist[k0];
      float dx = (1 - len/spr.rlen) * spr.dx;
      float dy = (1 - len/spr.rlen) * spr.dy;
      lp2.shiftBy( dx, dy );
      lp2 = lp2.mNext;
      ++ k0;
    }
    line.retracePath();

    return 0;
  }

  // return error codes
  //  -1   no selected point
  //  -2   selected point not on area border
  //  -3   no close line
  //  +1   only a point: nothing to follow
  //
  int snapHotItemToNearestLine()
  {
    SelectionPoint sp = mSelected.mHotItem;

    // no selected point or selected point not on area border:
    if ( sp == null ) return -1;
    if ( sp.type() != DrawingPath.DRAWING_PATH_AREA ) return -2;

    DrawingPath item = sp.mItem;
    DrawingAreaPath area = (DrawingAreaPath)item;
    // int k0 = 0;
    LinePoint q0 = sp.mPoint;

    // ArrayList< LinePoint > pts0 = area.mPoints;
    // int size0 = pts0.size();
    // for ( ; k0 < size0; ++k0 ) {
    //   if ( pts0.get(k0) == q0 ) break;
    // }
    // if ( k0 == size0 ) return false;
    // // area border: ... --> q2 --> q0 --> q1 --> ...
    // int k1 = (k0+1)%size0;
    // int k2 = (k0+size0-1)%size0;
    // LinePoint q1 = area.mPoints.get( k1 ); // next point on the area border
    // LinePoint q2 = area.mPoints.get( k2 ); // prev point on the area border
    LinePoint q1 = area.next( q0 );
    LinePoint q2 = area.prev( q0 );

    float x = q0.x;
    float y = q0.y;
    float thr = 10f;
    float dmin = thr; // require a minimum distance
    DrawingPointLinePath lmin = null;
    boolean min_is_area = false;
    // int kk0 = -1;

    // find drawing path with minimal distance from (x,y)
    LinePoint pp0 = null;

    for ( ICanvasCommand cmd : mCurrentStack ) {
      if ( cmd.commandType() != 0 ) continue;
      DrawingPath p = (DrawingPath)cmd;
      if ( p == item ) continue;
      if ( p.mType != DrawingPath.DRAWING_PATH_LINE &&
           p.mType != DrawingPath.DRAWING_PATH_AREA ) continue;
      DrawingPointLinePath lp = (DrawingPointLinePath)p;
      // ArrayList< LinePoint > pts = lp.mPoints;
      // int size = pts.size();
      // for ( int k=0; k<size; ++k ) 
      int ks = lp.size();
      for ( LinePoint pt = lp.mFirst; pt != null && ks > 0; pt = pt.mNext )
      {
        -- ks;
        // float d = pts.get(k).distance( x, y );
        float d = pt.distance( x, y );
        if ( d < dmin ) {
          dmin = d;
          // kk0 = k;
          pp0  = pt;
          lmin = lp;
          min_is_area = ( p.mType == DrawingPath.DRAWING_PATH_AREA );
        }
      }
    }
    if ( lmin == null ) return -3;
    int cmax = area.size() + 1;
    
    // if ( TDLog.LOG_DEBUG ) { // ===== FIRST SET OF LOGS
    //   TDLog.Debug( "snap to line");
    //   for ( LinePoint pt = lmin.mFirst; pt!=null; pt=pt.mNext ) TDLog.Debug( pt.x + " " + pt.y );
    //   TDLog.Debug( "snap area");
    //   for ( LinePoint pt = area.mFirst; pt!=null; pt=pt.mNext ) TDLog.Debug( pt.x + " " + pt.y );
    //   TDLog.Debug( "snap qq0= " + q0.x + " " + q0.y + " to pp0= " + pp0.x + " " + pp0.y );
    // }

    int ret = 0; // return code

    LinePoint pp1 = lmin.next( pp0 );
    LinePoint pp2 = lmin.prev( pp0 );

    LinePoint pp10 = null; // current point forward
    LinePoint pp20 = null; // current point backward
    // LinePoint pp1  = null; // next point forward
    // LinePoint pp2  = null; // prev point backwrad
    LinePoint qq10 = null;
    LinePoint qq20 = null;
    LinePoint qq1 = null;
    LinePoint qq2 = null;
    boolean reverse = false;
    int step = 1;
    // if ( kk1 >= 0 ) 
    if ( pp1 != null ) { 
      // TDLog.Debug( "snap pp1 " + pp1.x + " " + pp1.y + " FOLLOW LINE FORWARD" );
      // pp1  = pts1.get( kk1 );
      // pp10 = pts1.get( kk0 );
      pp10 = pp0;
      // if ( kk2 >= 0 ) 
      if ( pp2 != null ) {
        // TDLog.Debug( "snap pp2 " + pp2.x + " " + pp2.y );
        // pp2  = pts1.get( kk2 ); 
        // pp20 = pts1.get( kk0 ); 
        pp20 = pp0;
      }
      if ( pp1.distance( q1 ) < pp1.distance( q2 ) ) {
        qq1  = q1; // follow border forward
        qq10 = q0;
        // TDLog.Debug( "snap qq1 " + qq1.x + " " + qq1.y + " follow border forward" );
        if ( pp2 != null ) {
          qq2  = q2;
          qq20 = q0;
          // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y );
        }
      } else {
        reverse = true;
        qq1  = q2; // follow border backward
        qq10 = q0;
        // TDLog.Debug( "snap reverse qq1 " + qq1.x + " " + qq1.y + " follow border backward" );
        if ( pp2 != null ) {
          qq2 = q1;
          qq20 = q0;
          // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y + " follow forward");
        }
      }
    } else if ( pp2 != null ) { // pp10 is null
      // pp2  = pts1.get( kk2 ); 
      // pp20 = pts1.get( kk0 ); 
      pp20 = pp0;
      // TDLog.Debug( "snap pp1 null pp2 " + pp2.x + " " + pp2.y + " FOLLOW LINE BACKWARD" );
      if ( pp2.distance( q2 ) < pp2.distance( q1 ) ) {
        qq2 = q2;
        qq20 = q0;
        // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y + " follow border backward" );
      } else {
        reverse = true;
        qq2 = q1;
        qq20 = q0;
        // TDLog.Debug( "snap reverse qq2 " + qq2.x + " " + qq2.y + " follow border forward" );
      }
    } else {  // pp10 and pp20 are null: nothing to follow
      // copy pp0 to q0
      q0.x = pp0.x;
      q0.y = pp0.y;
      ret = 1;
    }

    if ( qq1 != null ) {
      // TDLog.Debug( "qq1 not null " + qq1.x + " " + qq1.y + " reverse " + reverse );
      // follow line pp10 --> pp1 --> ... using step 1
      // with border qq10 --> qq1 --> ... using step delta1

      for (int c=0; c<cmax; ++c) { // try to move qq1 forward
        TDLog.Debug( "snap at qq1 " + qq1.x + " " + qq1.y );
        float s = project( qq1, pp10, pp1 );
        while ( s > 1.0 ) {
          pp10 = pp1;
          // TDLog.Debug( "snap follow pp10 " + pp10.x + " " + pp10.y );
          pp1  = lmin.next( pp1 );
          if ( pp1 == null ) {
            // TDLog.Debug( "snap end of line pp1 null, pp10 " + pp10.x + " " + pp10.y );
            break;
          }
          if ( pp1 == pp0 ) {
            // TDLog.Debug( "snap pp1 == pp0, pp10 " + pp10.x + " " + pp10.y );
            break;
          }
          s = project( qq1, pp10, pp1 );
        }
        if ( pp1 == null ) break;
        float d1 = distance( qq1, pp10, pp1 );
        // TDLog.Debug( "distance d1 " + d1 + " s " + s );

        if ( s < 0.0f ) break;
        if ( d1 > thr || d1 < 0.001f ) break; 
        qq10 = qq1;
        qq1 = (reverse)? area.prev(qq1) : area.next( qq1 );
        if ( qq1 == q0 ) break;
      }
    } else {
      // TDLog.Debug( "snap qq1 null" );
      qq10 = q0; // FIXME
    }
    // if ( qq10 != null && pp10 != null ) {
    //   TDLog.Debug( "QQ10 " + qq10.x + " " + qq10.y + " PP10 " + pp10.x + " " + pp10.y );
    // }

    if ( qq2 != null ) {
      // TDLog.Debug( "qq2 not null: " + qq2.x + " " + qq2.y + " reverse " + reverse );
      // follow line pp20 --> pp2 --> ... using step size1-1
      // with border qq20 --> qq2 --> ... using step delta2
      for (int c=0; c < cmax; ++c) { // try to move qq2 backward
        // TDLog.Debug( "snap at qq2 " + qq2.x + " " + qq2.y );
        float s = project( qq2, pp20, pp2 );
        while ( s > 1.0 ) {
          pp20 = pp2;
          // TDLog.Debug( "snap s>1, follow pp20 " + pp20.x + " " + pp20.y );
          pp2 = lmin.prev( pp2 );
          if ( pp2 == null ) {
            // TDLog.Debug( "snap end of line pp2 null, pp20 " + pp20.x + " " + pp20.y );
            break;
          }
          if ( pp2 == pp0 ) {
            // TDLog.Debug( "snap pp2 == pp0, pp20 " + pp20.x + " " + pp20.y );
            break;
          }
          s = project( qq2, pp20, pp2 );
        }
        if ( pp2 == null ) break;
        float d2 = distance( qq2, pp20, pp2 );
        // TDLog.Debug( "distance qq2-P_line " + d2 + " s " + s );

        if ( s < 0.0f ) break;
        if ( d2 > thr || d2 < 0.001f ) break; 
        qq20 = qq2;
        qq2 = (reverse)? area.next(qq2) : area.prev( qq2 );
        if ( qq2 == q0 ) break;
      }
    } else {
      // TDLog.Debug( "snap qq2 null");
      qq20 = q0; // FIXME
    }
    // if ( qq20 != null && pp20 != null ) {
    //   TDLog.Debug( "QQ20 " + qq20.x + " " + qq20.y + " PP20 " + pp20.x + " " + pp20.y );
    // }

    if ( qq20 == qq10 || (reverse && pp10 == null) || (!reverse && pp20 == null) ) {
      // should not happen, anyways copy pp0 to q0
      q0.x = pp0.x;
      q0.y = pp0.y;
      ret = 2;
    }

    synchronized( mCurrentStack ) {
      if ( ret == 0 ) { 
        synchronized( mSelection ) {
          mSelection.removePath( area );
        }
        // next-prev refer to the point list along the area path.
        LinePoint next = qq10.mNext; // unlink qq20 -> ... -> qq10
        LinePoint prev = qq20.mPrev;
        if ( reverse ) {             // unlink qq10 -> ... -> qq20
          next = qq20.mNext;
          prev = qq10.mPrev;
        } 

        if ( prev == null ) {
          area.mFirst = null; // ( reverse )? qq10 : qq20;
          // TDLog.Debug( "snap setting area FIRST null ");
        } else {
          // TDLog.Debug( "snap start prev " + prev.x + " " + prev.y );
          LinePoint q = prev;
          while ( prev != null && prev != next ) {
            q = prev;
            prev = q.mPrev;
          }
          area.mFirst = q;
          if ( q.mPrev != null ) { // make sure first has no prev
            q.mPrev.mNext = null;
            q.mPrev = null;
          }
          // TDLog.Debug( "snap setting area FIRST " + area.mFirst.x + " " + area.mFirst.y );
        }

        if ( next == null ) {
          area.mLast = null; // ( reverse )? qq20 : qq10;
          // TDLog.Debug( "snap setting area LAST null ");
        } else {
          // TDLog.Debug( "snap start next " + next.x + " " + next.y );
          LinePoint q = next;
          while ( next != null && next != prev ) {
            q = next;
            next = q.mNext;
          }
          area.mLast = q;
          if ( q.mNext != null ) {
            q.mNext.mPrev = null;
            q.mNext = null;
          }
          // TDLog.Debug( "snap setting area LAST " + area.mLast.x + " " + area.mLast.y );
        }

        next = (reverse)? qq20 : qq10;

        // insert points pp20 - ... - pp10 (included)
        if ( reverse ) {
          LinePoint q = qq10.mPrev;
          LinePoint p = pp10;
          if ( q != null ) {
            // TDLog.Debug( "snap attach at " + q.x + " " + q.y );
          } else {
            // TDLog.Debug( "snap restart area ");
          }
          q = new LinePoint( p.x, p.y, q );
          // TDLog.Debug( "snap first new point " + q.x + " " + q.y );
          if ( p != pp20 ) {
            p = p.mPrev;
            if ( area.mFirst == null ) area.mFirst = q;
            for ( ; p != null && p != pp20; p = p.mPrev ) {
              if ( p.has_cp && p != pp10 ) {
                LinePoint pp = p.mNext;
                q = new LinePoint( pp.x2, pp.y2, pp.x1, pp.y1, p.x, p.y, q );
              } else {
                q = new LinePoint( p.x, p.y, q );
              }
              // TDLog.Debug( "snap new point " + q.x + " " + q.y );
            }
            if ( p != null ) { // FIXME add last point
              if ( p.has_cp ) {
                LinePoint pp = p.mNext;
                q = new LinePoint( pp.x2, pp.y2, pp.x1, pp.y1, p.x, p.y, q );
              } else {
                q = new LinePoint( p.x, p.y, q );
              }
              // TDLog.Debug( "snap last new point " + q.x + " " + q.y );
            }
          }
          q.mNext = next;
          if ( next != null ) {
            next.mPrev  = q;
            next.has_cp = false; // enforce straight segment
          }
          if ( area.mLast == null ) area.mLast = q;

        } else { // not reverse

          LinePoint q = qq20.mPrev;
          LinePoint p = pp20;
          if ( q != null ) {
            // TDLog.Debug( "snap attach at " + q.x + " " + q.y );
          } else {
            // TDLog.Debug( "snap restart area ");
          }
          q = new LinePoint( p.x, p.y, q );
          // TDLog.Debug( "snap first new point " + q.x + " " + q.y );
          if ( p != pp10 ) {
            p = p.mNext;
            if ( area.mFirst == null ) area.mFirst = q;
            for ( ; p != null && p != pp10; p = p.mNext ) {
              q = new LinePoint( p, q );
              // TDLog.Debug( "snap new point " + q.x + " " + q.y );
            }
            // if ( p != null ) { // FIXME not add "last" point
            //   q = new LinePoint( p, q );
            //   TDLog.Debug( "snap last new point " + q.x + " " + q.y );
            // }
          }
          q.mNext = next;
          if ( next != null ) {
            next.mPrev  = q;
            next.has_cp = false;
          }
          if ( area.mLast == null ) area.mLast = q;
        }
        area.recount(); 
        // TDLog.Debug( "snap new size " + area.size() );
      }

      // area.mPoints = pts2;
      area.retracePath();
      
      if ( ret == 0 ) {
        synchronized( mSelection ) {
          mSelection.insertPath( area );
        }
      }
      clearSelected();
    }
    // checkLines();
    return ret;
  }

  SelectionPoint hotItem() { return mSelected.mHotItem; }

  boolean hasSelected() { return mSelected.mPoints.size() > 0; }

  void rotateHotItem( float dy )
  { 
    synchronized( mSelection ) {
      mSelected.rotateHotItem( dy );
    }
  }

  // void shiftHotItem( float dx, float dy, float range ) 
  void shiftHotItem( float dx, float dy )
  { 
    synchronized( mSelection ) {
      // SelectionPoint sp = mSelected.shiftHotItem( dx, dy, range );
      SelectionPoint sp = mSelected.shiftHotItem( dx, dy );
      // DrawingPath path = sp.mItem;
      // if ( path.mType == DrawingPath.DRAWING_PATH_POINT || DrawingWindow.mEditRadius == 0 ) {
      //   mSelection.checkBucket( sp );
      // } else {
      //   mSelection.rebucketLinePath( (DrawingPointLinePath)path );
      // }
      mSelection.checkBucket( sp );
    }
  }

  SelectionPoint nextHotItem() { return mSelected.nextHotItem(); }
  SelectionPoint prevHotItem() { return mSelected.prevHotItem(); }

  // used by flipProfile
  // void rebuildSelection()
  // {
  //   Selection selection = new Selection();
  //   synchronized ( mCurrentStack ) {
  //     final Iterator i = mCurrentStack.iterator();
  //     while ( i.hasNext() ) {
  //       final ICanvasCommand cmd = (ICanvasCommand) i.next();
  //       if ( cmd.commandType() != 0 ) continue;
  //       DrawingPath path = (DrawingPath) cmd;
  //       selection.insertPath( path );
  //       // switch ( path.mType ) {
  //       //   case DrawingPath.DRAWING_PATH_POINT:
  //       //   case DrawingPath.DRAWING_PATH_LINE;
  //       //   case DrawingPath.DRAWING_PATH_AREA:
  //       //     selection.insertPath( path );
  //       //     break;
  //       // }
  //     }
  //   }
  //   mSelection = selection;
  // }

  RectF computeBBox() 
  {
    float xmin=1000000f, xmax=-1000000f, 
          ymin=1000000f, ymax=-1000000f;
    synchronized( mCurrentStack ) {
      final Iterator i = mCurrentStack.iterator();
      while ( i.hasNext() ) {
        final ICanvasCommand cmd = (ICanvasCommand) i.next();
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;
        // RectF bbox = p.mBBox;
        if ( p.left   < xmin ) xmin = p.left;
        if ( p.right  > xmax ) xmax = p.right;
        if ( p.top    < ymin ) ymin = p.top;
        if ( p.bottom > ymax ) ymax = p.bottom;
      }
    }
    return new RectF( xmin, ymin, xmax, ymax ); // left top right bottom
  }

  // FIXME DataHelper and SID are necessary to export splays by the station
  public void exportTherion( // DataHelper dh, long sid,
                             int type, BufferedWriter out, String scrap_name, String proj_name, int proj_dir )
  {
    RectF bbox = computeBBox();
    DrawingIO.exportTherion( // dh, sid,
                             type, out, scrap_name, proj_name, proj_dir, bbox, mNorthLine,
                             mCurrentStack, mUserStations, mStations, mSplaysStack );
  }
   
  public void exportDataStream( int type, DataOutputStream dos, String scrap_name, int proj_dir )
  {
    RectF bbox = computeBBox();
    DrawingIO.exportDataStream( type, dos, scrap_name, proj_dir, bbox, mNorthLine, mCurrentStack, mUserStations, mStations );
  }

  void exportAsCsx( PrintWriter pw, String cave, String branch )
  {
    synchronized( mCurrentStack ) {
      pw.format("    <layers>\n");

      // LAYER 0: images and sketches
      pw.format("      <layer name=\"Base\" type=\"0\">\n");
      pw.format("         <items>\n");
      pw.format("         </items>\n");
      pw.format("      </layer>\n");

      // LAYER 1: soil areas
      pw.format("      <layer name=\"Soil\" type=\"1\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;
        if ( p.mType == DrawingPath.DRAWING_PATH_AREA ) {
          DrawingAreaPath ap = (DrawingAreaPath)p;
          if ( BrushManager.getAreaCsxLayer( ap.mAreaType ) != 1 ) continue;
          ap.toCsurvey( pw, cave, branch );
        }
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");

      // LAYER 2: 
      pw.format("      <layer name=\"Water and floor morphologies\" type=\"2\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;

        if ( p.mType == DrawingPath.DRAWING_PATH_LINE ) {
          DrawingLinePath lp = (DrawingLinePath)p;
          if ( BrushManager.getLineCsxLayer( lp.mLineType ) != 2 ) continue;
          lp.toCsurvey( pw, cave, branch );
        } else if ( p.mType == DrawingPath.DRAWING_PATH_AREA ) {
          DrawingAreaPath ap = (DrawingAreaPath)p;
          if ( BrushManager.getAreaCsxLayer( ap.mAreaType ) != 2 ) continue;
          ap.toCsurvey( pw, cave, branch );
        } 
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");

      // LAYER 3
      pw.format("      <layer name=\"Rocks and concretions\" type=\"3\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;

	if ( p.mType == DrawingPath.DRAWING_PATH_LINE ) {
	  DrawingLinePath lp = (DrawingLinePath)p;
	  if ( BrushManager.getLineCsxLayer( lp.mLineType ) != 3 ) continue;
	  lp.toCsurvey( pw, cave, branch );
        }
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");

      // LAYER 4
      pw.format("      <layer name=\"Ceiling morphologies\" type=\"4\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;

        if ( p.mType == DrawingPath.DRAWING_PATH_LINE ) {
          DrawingLinePath lp = (DrawingLinePath)p;
          if ( BrushManager.getLineCsxLayer( lp.mLineType ) != 4 ) continue;
          lp.toCsurvey( pw, cave, branch );
        }
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");

      // LAYER 5:
      pw.format("      <layer name=\"Borders\" type=\"5\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;

        if ( p.mType == DrawingPath.DRAWING_PATH_LINE ) {
          DrawingLinePath lp = (DrawingLinePath)p;
          if ( BrushManager.getLineCsxLayer( lp.mLineType ) != 5 ) continue;
          lp.toCsurvey( pw, cave, branch );
        }
        // if ( lp.lineType() == BrushManager.mLineLib.mLineWallIndex ) {
        //   // linetype: 0 line, 1 spline, 2 bezier
        //   pw.format("          <item layer=\"5\" name=\"\" type=\"4\" category=\"1\" linetype=\"0\" mergemode=\"0\">\n");
        //   pw.format("            <pen type=\"1\" />\n");
        //   pw.format("            <points data=\"");
        //   ArrayList< LinePoint > pts = lp.mPoints;
        //   boolean b = true;
        //   for ( LinePoint pt : pts ) {
        //     float x = DrawingWindow.sceneToWorldX( pt.x );
        //     float y = DrawingWindow.sceneToWorldY( pt.y );
        //     pw.format(Locale.US, "%.2f %.2f ", x, y );
        //     if ( b ) { pw.format("B "); b = false; }
        //   }
        //   pw.format("\" />\n");
        //   pw.format("          </item>\n");
        // }
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");

      // LAYER 6: signs and texts
      pw.format("      <layer name=\"Signs\" type=\"6\">\n");
      pw.format("        <items>\n");
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;

        if ( p.mType == DrawingPath.DRAWING_PATH_POINT ) {
          DrawingPointPath pp = (DrawingPointPath)p;
          // section points are special
          if ( pp.mPointType == BrushManager.mPointLib.mPointSectionIndex ) {
            // Log.v("DistoX", "Section point <" + pp.mOptions + ">");
            // option: -scrap survey-xx#
            // TODO open file survey-xx#.tdr and convert it to csurvey
          }
          if ( BrushManager.getPointCsxLayer( pp.mPointType ) != 6 ) continue;
          pp.toCsurvey( pw, cave, branch );
        }
      }
      pw.format("        </items>\n");
      pw.format("      </layer>\n");
      pw.format("    </layers>\n");
    }
  }

  DrawingAudioPath getAudioPoint( long bid )
  {
    synchronized( mCurrentStack ) {
      final Iterator i = mCurrentStack.iterator();
      while ( i.hasNext() ){
        final ICanvasCommand cmd = (ICanvasCommand) i.next();
        if ( cmd.commandType() == 0 ) {
          DrawingPath path = (DrawingPath)cmd;
          if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            DrawingPointPath pt = (DrawingPointPath)path;
            if ( pt.mPointType == BrushManager.mPointLib.mPointAudioIndex ) {
              DrawingAudioPath audio = (DrawingAudioPath)pt;
              if ( audio.mId == bid ) return audio;
            }
          }
        }
      }
    }
    return null;
  }
 
  // called by DrawingSurface::addDrawingStationName
  public void addStation( DrawingStationName st, boolean selectable )
  {
    // Log.v("DistoX", "add station " + st.name() + " scene " + st.cx + " " + st.cy + " XSection " + st.mXSectionType );
    synchronized( mStations ) {
      mStations.add( st );
      if ( selectable ) {
        synchronized( mSelection ) {
          // Log.v( "DistoX", "selection add station " + st.name() );
          mSelection.insertStationName( st );
        }
      }
    }
  }

  // this is not efficient: the station names should be stored in a tree (key = name) for log-time search
  // type = type of the plot
  void setStationXSections( List<PlotInfo> xsections, long type )
  {
    for ( DrawingStationName st : mStations ) {
      String name = st.name();
      // Log.v( "DistoX", "Station <" + name + ">" );
      for ( PlotInfo plot : xsections ) {
        if ( name.equals( plot.start ) ) {
          st.setXSection( plot.azimuth, plot.clino, type );
          break;
        }
      }
    }
  }

  float computeSectionArea()
  {
    float ret = 0;
    for ( ICanvasCommand icc : mCurrentStack ) {
      if ( icc.commandType() != 0 ) continue;
      DrawingPath p = (DrawingPath)icc;
      if ( p.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
      DrawingLinePath lp = (DrawingLinePath)p;
      if ( lp.mLineType != BrushManager.mLineLib.mLineWallIndex ) continue;
      LinePoint pt = lp.mFirst;
      while ( pt != lp.mLast ) {
        LinePoint pn = pt.mNext;
        ret += pt.y * pn.x - pt.x * pn.y;
        pt = pn;
      }
    }
    return ret / 2;
  }

  void clearScrapOutline() { synchronized( mScrap ) { mScrap.clear(); } }

  void addScrapDataStream( String tdr, float xdelta, float ydelta )
  {
    synchronized( mScrap ) {
      mScrap.clear();
    }
  }


}
