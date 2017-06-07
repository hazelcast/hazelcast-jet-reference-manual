In the abstract sense, the session window is a quite intuitive concept:
it simply captures a burst of events. As soon as the gap between two
events exceeds the configured session timeout, the window closes.
However, because the Jet processor encounters events out of their
original order, this kind of window becomes quite tricky to compute.

The way Jet computes the session windows is easiest to explain in terms
of the _event interval_: the range
`[eventTimestamp, eventTimestamp + sessionTimeout]`.
Initially an event causes a new session window to be created, covering
exactly the event interval. 

<img alt="Session window: single event" 
    src="../images/session-window-1.png"
    width="200"/>

A following event under the same key belongs to this window iff its
interval overlaps it. The window is extended to cover the entire
interval of the new event. 

<img alt="Session window: extend with another event" 
    src="../images/session-window-2.png"
    width="110"/>
    
If the event interval don't overlap, a new session window is created for
the new event.

<img alt="Session window: create a new window after session timeout" 
    src="../images/session-window-3.png"
    width="240"/>

The event may happen to belong to two existing windows if its interval
bridges the gap between them; in that case they are combined into one.

<img alt="Session window: an event may merge two existing windows" 
    src="../images/session-window-4.png"
    width="240"/>

The session window is an example of a window that cannot be computed in
a two-stage setup. A processor observing only a subset of events would
construct entirely different windows and the local punctuation would not
be enough to conclude when a given window is ready to be finalized and
emitted.
