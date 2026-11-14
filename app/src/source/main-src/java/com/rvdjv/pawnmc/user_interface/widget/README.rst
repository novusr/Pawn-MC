Shared widgets (``user_interface/widget``)
==========================================

Composable controls used by more than one screen. Today that is one file, and it exists
because the alternative was six copies of the same scrollbar.


``ScrollSidebar.kt``
    A slim right-edge scroll sidebar: a faint track plus a draggable thumb whose *length*
    is the share of the content currently on screen, so a long compiler log reads as long
    before it is touched. Two entry points:

    * ``SimpleScrollSidebar(scrollState)`` for a ``Column`` with ``verticalScroll``;
    * ``LazyScrollSidebar(listState)`` for a lazy list, where position and length come
      from the laid-out items rather than from the item count.

    Both return nothing when the content already fits. The strip measures its own height
    with ``BoxWithConstraints``, which is why the same call works in a 240 dp output card
    and on a full-height settings page.

    ``ScrollSidebarDefaults`` holds the width, thumb width, minimum thumb length and track
    padding; ``LabeledScrollSidebar`` adds a start/end counter for screens where the
    length itself is information (the PocketSAMP console).


Why PocketSAMP has its own copy
-------------------------------

``thirdparty/PocketSAMP/app/src/main/java/com/pocketsamp/server/user_interface/widget/``
contains the same component. PocketSAMP is a separate Gradle module, so it cannot import
from ``com.rvdjv.pawnmc`` without reversing the dependency. The two copies are intentional
and must be changed together: a behaviour fix in one that is not mirrored in the other
shows up as "the sidebar works in the editor but not on the console".


Rules that are easy to break
----------------------------

* **The thumb is a scrollbar, not a pane.** It has no background surface and no elevation;
  giving it one makes it look like a separate panel that happens to sit on the card.
* **Drag follows the finger.** The drag starts by jumping the thumb to the touch point and
  then accumulates from that fraction; accumulating raw deltas from a stale position
  loses movement on long drags.
* **Minimum thumb length is deliberate.** A 10,000-line file would otherwise get a
  hairline; ``MinThumbHeight`` keeps something to grab.
