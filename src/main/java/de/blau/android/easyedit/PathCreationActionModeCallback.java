package de.blau.android.easyedit;

import java.util.ArrayList;
import java.util.Iterator;

import android.content.res.ColorStateList;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import de.blau.android.Map;
import de.blau.android.R;
import de.blau.android.exception.OsmIllegalOperationException;
import de.blau.android.osm.Node;
import de.blau.android.osm.ViewBox;
import de.blau.android.osm.Way;
import de.blau.android.util.GeoMath;
import de.blau.android.util.Snack;
import de.blau.android.util.ThemeUtils;
import de.blau.android.util.Util;

/**
 * This callback handles path creation. It is started after a long-press. During this action mode, clicks are handled by
 * custom code. The node and way click handlers are thus never called.
 */
public class PathCreationActionModeCallback extends NonSimpleActionModeCallback {
    private static final String DEBUG_TAG              = "PathCreationAction...";
    private static final int    MENUITEM_UNDO          = 1;
    private static final int    MENUITEM_NEWWAY_PRESET = 2;

    /** x coordinate of first node */
    private float   x;
    /** y coordinate of first node */
    private float   y;
    /** Node to append to */
    private Node    appendTargetNode;
    /** Way to append to */
    private Way     appendTargetWay;
    /** flag if we don't want to start the property editor in onDestroy **/
    private boolean dontTag = false;
    private boolean loc = false;
    private Location location;

    /** contains a pointer to the created way if one was created. used to fix selection after undo. */
    private Way             createdWay   = null;
    /** contains a list of created nodes. used to fix selection after undo. */
    private ArrayList<Node> createdNodes = new ArrayList<>();

    private Map map;
    private ViewBox box;
    private int width;
    private int height;
    private int warning = 0;
    private FloatingActionButton fab;
    private Boolean longClickToggle;

    /**
     * Construct a new PathCreationActionModeCallback starting with screen coordinates
     *
     * @param manager the current EasyEditManager instance
     * @param x screen x
     * @param y screen y
     */
    public PathCreationActionModeCallback(@NonNull EasyEditManager manager, float x, float y) {
        super(manager);
        this.loc = false;
        this.map = main.getMap();
        this.box = map.getViewBox();
        this.width = map.getWidth();
        this.height = map.getHeight();
        this.x = x;
        this.y = y;

        appendTargetNode = null;
        appendTargetWay = null;

    }

    public PathCreationActionModeCallback(@NonNull EasyEditManager manager, boolean mainloc) {
        super(manager);
        this.loc = loc;
        this.map = main.getMap();
        this.box = map.getViewBox();
        this.width = map.getWidth();
        this.height = map.getHeight();

        appendTargetNode = null;
        appendTargetWay = null;
    }


    /**
     * Construct a new PathCreationActionModeCallback starting with an existing Node
     *
     * @param manager the current EasyEditManager instance
     * @param node the existing Node
     */
    public PathCreationActionModeCallback(@NonNull EasyEditManager manager, @NonNull Node node) {
        super(manager);
        appendTargetNode = node;
        appendTargetWay = null;

        this.map = main.getMap();
        this.box = map.getViewBox();
        this.width = map.getWidth();
        this.height = map.getHeight();
        Log.i("MEINELOCATIONE", "append node");
    }

    /**
     * Construct a new PathCreationActionModeCallback starting with an existing Way and an existing Node to add
     *
     * @param manager the current EasyEditManager instance
     * @param way the exiting Way
     * @param node the existign Node to add
     */
    public PathCreationActionModeCallback(@NonNull EasyEditManager manager, @NonNull Way way, @NonNull Node node) {
        super(manager);


        this.map = main.getMap();
        this.box = map.getViewBox();
        this.width = map.getWidth();
        this.height = map.getHeight();

        appendTargetNode = node;
        appendTargetWay = way;
        Log.i("MEINELOCATIONE", "append node way");
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        helpTopic = R.string.help_pathcreation;
        super.onCreateActionMode(mode, menu);
        Log.i("MEINELOCATIONE", "oncreate");
        mode.setSubtitle(R.string.actionmode_createpath);
        logic.setSelectedWay(null);
        logic.setSelectedNode(appendTargetNode);
        if (appendTargetNode != null) {
            if (appendTargetWay != null) {
                logic.performAppendStart(appendTargetWay, appendTargetNode);
            } else {
                logic.performAppendStart(appendTargetNode);
            }
        } else {
            try {
                pathCreateNode(x, y);
            } catch (OsmIllegalOperationException e) {
                Snack.barError(main, e.getLocalizedMessage());
            }
        }
        logic.hideCrosshairs();
        return true;
    }

    @Override
    public boolean handleClick(float x, float y) {
        Log.i("MEINELOCATIONE", "IN handleclick");


        super.handleClick(x, y);
        try {

            pathCreateNode(x, y);
        } catch (OsmIllegalOperationException e) {
            Snack.barError(main, e.getLocalizedMessage());
        }
        return true;
    }

    /**
     * Creates/adds a node into a path during path creation
     *
     * @param x x screen coordinate
     * @param y y screen coordinate
     */
    private void pathCreateNode(float x, float y) {
        Log.i("MEINELOCATIONE", "IN pathcreate");


        if (main.loc) {
            Log.i("MEINELOCATIONE", "IN loc=true");
            Location location = main.getLastLocation();
            x = GeoMath.lonToX(width, box, location.getLongitude());
            y = GeoMath.latToY(height, width, box, location.getLatitude());

        }


        Node lastSelectedNode = logic.getSelectedNode();
        Way lastSelectedWay = logic.getSelectedWay();



        if (appendTargetNode != null) {
            Log.i("MEINELOCATIONE", "perform append append");
            logic.performAppendAppend(main, x, y);
        } else {
            Log.i("MEINELOCATIONE", "perform add");
            logic.performAdd(main, x, y);
        }
        if (logic.getSelectedNode() == null) {


            // user clicked last node again -> finish adding
            Log.i("MEINELOCATIONE", "user clicked last node again -> finish adding");
            delayedResetHasProblem(lastSelectedWay);
            manager.finish();
            tagApplicable(lastSelectedNode, lastSelectedWay, true, false);

        } else { // update cache for undo
            Log.i("MEINELOCATIONE", "createdWay");
            createdWay = logic.getSelectedWay();
            if (createdWay == null) {
                Log.i("MEINELOCATIONE", "array");
                createdNodes = new ArrayList<>();
            } else {
                createdWay.dontValidate();
            }
            createdNodes.add(logic.getSelectedNode());
        }
        main.invalidateMap();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu = replaceMenu(menu, mode, this);
        super.onPrepareActionMode(mode, menu);
        menu.clear();
        menuUtil.reset();
        menu.add(Menu.NONE, MENUITEM_UNDO, Menu.NONE, R.string.undo).setAlphabeticShortcut(Util.getShortCut(main, R.string.shortcut_undo))
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_undo));
        menu.add(Menu.NONE, MENUITEM_NEWWAY_PRESET, Menu.NONE, R.string.tag_menu_preset).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_preset));
        menu.add(GROUP_BASE, MENUITEM_HELP, Menu.CATEGORY_SYSTEM | 10, R.string.menu_help).setAlphabeticShortcut(Util.getShortCut(main, R.string.shortcut_help))
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_help));
        arrangeMenu(menu);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        super.onActionItemClicked(mode, item);
        switch (item.getItemId()) {
        case MENUITEM_UNDO:
            handleUndo();
            break;
        case MENUITEM_NEWWAY_PRESET:
            Way lastSelectedWay = logic.getSelectedWay();
            if (lastSelectedWay != null) {
                dontTag = true;
                main.startSupportActionMode(new WaySelectionActionModeCallback(manager, lastSelectedWay));
                // show preset screen
                main.performTagEdit(lastSelectedWay, null, false, item.getItemId() == MENUITEM_NEWWAY_PRESET, false);
            }
            return true;
        default:
            Log.e(DEBUG_TAG, "Unknown menu item");
            break;
        }
        return false;
    }

    /**
     * Handle presses on the undo button, this does not invoke the normal undo mechanism but simply removes the
     * non-saved nodes one by one
     */
    private void handleUndo() {
        logic.undo();
        if (logic.getSelectedNode() == null) { // should always happen when we added a new node and removed it
            Iterator<Node> nodeIterator = createdNodes.iterator();
            while (nodeIterator.hasNext()) { // remove nodes that do not exist anymore
                if (!logic.exists(nodeIterator.next())) {
                    nodeIterator.remove();
                }
            }
        } else {
            // remove existing node from list
            createdNodes.remove(logic.getSelectedNode());
        }
        // exit or select the previous node
        if (createdNodes.isEmpty()) {
            logic.setSelectedNode(null);
            // all nodes have been deleted, cancel action mode
            manager.finish();
        } else {
            // select last node
            logic.setSelectedNode(createdNodes.get(createdNodes.size() - 1));
        }

        createdWay = logic.getSelectedWay(); // will be null if way was deleted by undo
        main.invalidateMap();
    }

    /**
     * Path creation action mode is ending
     */
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        final Node lastSelectedNode = logic.getSelectedNode();
        final Way lastSelectedWay = logic.getSelectedWay();
        logic.setSelectedWay(null);
        logic.setSelectedNode(null);
        super.onDestroyActionMode(mode);
        if (appendTargetNode == null && !dontTag) { // doesn't work as intended element selected modes get zapped,
                                                    // don't try to select because of this
            tagApplicable(lastSelectedNode, lastSelectedWay, false, false);
            delayedResetHasProblem(lastSelectedWay);
        }
    }

    /**
     * Hackish way of suppressing unnecessary validation failures
     *
     * 1 sec seems to be enough to avoid invalidations and by that redraws of the map
     *
     * @param way the way that we want to enable validation on
     */
    private void delayedResetHasProblem(@NonNull final Way way) {
        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (way != null) {
                    way.resetHasProblem(); // remove Validator.OK
                }
            }
        };
        Map map = main.getMap();
        if (map != null) {
            map.postDelayed(run,1000);
        }
    }
}
