/* @file DataDownloadTask.java
 *
 * @author marco corvi
 * @date feb 2012
 *
 * @brief TopoDroid batch data-download task
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import java.util.ArrayList;

import android.widget.Toast;
import android.os.AsyncTask;
import android.os.Handler;

import android.util.Log;

public class DataDownloadTask extends AsyncTask< String, Integer, Integer >
{
  private TopoDroidApp mApp;
  private static DataDownloadTask running = null;
  // private ILister mLister;
  private ListerHandler mLister; // FIXME LISTER

  DataDownloadTask( TopoDroidApp app, ListerHandler /* ILister */ lister ) // FIXME LISTER
  {
    // TDLog.Error( "DataDownloadTask cstr" );
    mApp = app;
    mLister = lister;
  }

// -------------------------------------------------------------------
  @Override
  protected Integer doInBackground( String... statuses )
  {
    if ( ! lock() ) return null;
    // long time = System.currentTimeMillis();
    int nRead = mApp.downloadDataBatch( mLister );
    // time = System.currentTimeMillis() - time;
    // Log.v("DistoX", "READ " + nRead + " data in " + time + " msec");

    // if ( nRead < 0 ) {
    //   Toast.makeText( mApp.getApplicationContext(), mApp.DistoXConnectionError[ -nRead ], Toast.LENGTH_SHORT ).show();
    // TDLog.Error( "doInBackground read " + nRead );
    return nRead;
  }

  // @Override
  // protected void onProgressUpdate( Integer... values)
  // {
  //   super.onProgressUpdate( values );
  //   // TDLog.Log( TDLog.LOG_COMM, "onProgressUpdate " + values );
  // }

  @Override
  protected void onPostExecute( Integer res )
  {
    // TDLog.Log( TDLog.LOG_COMM, "onPostExecute res " + res );
    if ( res != null ) {
      int r = res.intValue();
      mLister.refreshDisplay( r, true );  // true: toast a message
    }
    mApp.mDataDownloader.setDownload( false );
    mApp.mDataDownloader.notifyConnectionStatus( false );
    unlock();
  }

  private synchronized boolean lock()
  {
    if ( running != null ) return false;
    running = this;
    return true;
  }

  private synchronized void unlock()
  {
    if ( running == this ) running = null;
  }

}
