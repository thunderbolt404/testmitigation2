# CCN-DTN Router for The ONE Simulator

## Overview

This project implements a **Content-Centric Networking (CCN) forwarding plane over a Delay-Tolerant Networking (DTN) environment** using **The ONE Simulator**.

The main router is `CCNRouter`, which extends The ONE's `ActiveRouter` and combines CCN mechanisms with selectable DTN forwarding strategies.

The current design supports three forwarding modes:

- `EPIDEMIC`
- `SPRAY_AND_WAIT`
- `PROPHET`

The CCN layer is responsible for Interest/Data processing, caching, Pending Interest Table (PIT) management, duplicate suppression, and local content delivery. The selected DTN forwarding mode controls how the resulting ONE messages are carried between intermittently connected nodes.

---

## Main Design Goal

The implementation keeps **one CCN-aware router class**:

```text
CCNRouter
   |
   +-- EPIDEMIC
   |
   +-- SPRAY_AND_WAIT
   |
   +-- PROPHET
```

Therefore, the recommended configuration is:

```text
Group.router = CCNRouter
```

and the DTN forwarding strategy is selected with:

```text
CCNRouter.forwardingMode = EPIDEMIC
```

or:

```text
CCNRouter.forwardingMode = SPRAY_AND_WAIT
```

or:

```text
CCNRouter.forwardingMode = PROPHET
```

This design allows all nodes to retain CCN functions such as PIT and caching while changing only the DTN carrier/forwarding strategy.

---

# 1. CCN Node Modes

`CCNRouter` supports three logical node roles.

| Mode | Value | Role |
|---|---:|---|
| Consumer | `1` | Generates content Interests and receives Data |
| Producer | `2` | Stores/serves static content |
| Intermediate | `3` | Relays Interests/Data and can use opportunistic caching |

Example:

```text
Group1.mode = 2
Group6.mode = 1
Group7.mode = 3
```

---

# 2. Main CCN Components

## 2.1 Pending Interest Table (PIT)

The PIT stores pending Interests for requested content.

Main purposes:

- Records outstanding content requests.
- Aggregates multiple Interests for the same content.
- Prevents unnecessary repeated forwarding.
- Supports multiple pending requesters.
- Removes a PIT entry when the corresponding Data is received and satisfied.
- Clears expired PIT entries according to `pitTimeout`.

Configuration:

```text
CCNRouter.pitTimeout = 600
GroupX.enablePIT = true
```

---

## 2.2 Static Cache

The Static Cache is mainly used by producer nodes to store available content.

It is controlled using settings such as:

```text
GroupX.staticCacheCapacity = 199
GroupX.staticCacheRange = 1,200
GroupX.seedStaticCache = 36
GroupX.avaCache = 1,1
```

In `avaCache`:

```text
1,1
```

means Static Cache and Opportunistic Cache are enabled.

For nodes that should not use Static Cache:

```text
0,1
```

means Static Cache is disabled while Opportunistic Cache remains enabled.

---

## 2.3 Opportunistic Cache

Nodes can cache received Data when opportunistic caching is enabled.

Example:

```text
GroupX.cacheCapacity = 500
GroupX.avaCache = 0,1
```

This enables intermediate nodes to reuse previously received content and potentially answer future Interests without contacting the original producer.

---

## 2.4 Duplicate Suppression

The router maintains bounded state for previously processed:

- Interest packets
- Data packets

Configuration:

```text
CCNRouter.processedListCapacity = 500
```

This means that up to 500 processed entries are retained in each duplicate-suppression state structure.

When the capacity is exceeded, the oldest remembered duplicate-state entry is removed.

This setting controls **duplicate-history memory**, not the cache capacity.

---

# 3. Interest Processing Pipeline

The implemented Interest processing order is:

```text
Interest arrives
      |
      v
Duplicate Interest / nonce check
      |
      +---- duplicate ----> suppress incoming message
      |
      v
Static Cache lookup
      |
      +---- hit ----------> generate Data response
      |
      v
Opportunistic Cache lookup
      |
      +---- hit ----------> generate Data response
      |
      v
PIT processing
      |
      +---- aggregated ---> suppress duplicate forwarding
      |
      +---- duplicate ----> suppress incoming message
      |
      v
Forward Interest through selected DTN mode
```

The intended processing order is therefore:

```text
Duplicate Suppression
        ->
Static Cache
        ->
Opportunistic Cache
        ->
PIT
        ->
DTN Forwarding
```

---

# 4. Data Processing Pipeline

The Data processing order is:

```text
Data arrives
    |
    v
Duplicate Data check
    |
    +---- duplicate ----> suppress incoming Data
    |
    v
Insert into Opportunistic Cache
    |
    v
Check PIT
    |
    +---- matching PIT ---> satisfy all pending requesters
    |                       and remove PIT entry
    |
    v
Check local destination
    |
    +---- local ----------> deliver to consumer
```

A Data packet can therefore satisfy several aggregated Interests through the same PIT entry.

---

# 5. Consumer Request Flow

A local consumer creates an Interest through:

```java
requestContent(contentName, requestId)
```

The router first checks:

1. Static Cache
2. Opportunistic Cache
3. PIT

If the content is not locally available and the Interest is not aggregated, the router forwards the Interest into the DTN.

Interest messages use:

```text
CCNRouter.interestSize = 50
```

Data messages use:

```text
CCNRouter.contentSize = 150
```

These values represent the simulated ONE message sizes used for Interest and Data traffic.

---

# 6. Forwarding Modes

## 6.1 Epidemic Forwarding

Configuration:

```text
CCNRouter.forwardingMode = EPIDEMIC
```

Epidemic mode tries to forward available messages through connected nodes without Spray-and-Wait copy restrictions or PRoPHET delivery-predictability selection.

Conceptually:

```text
Message
   |
   +--> Connected Node A
   +--> Connected Node B
   +--> Connected Node C
```

This can improve dissemination but may create more transmissions and overhead.

---

## 6.2 Spray-and-Wait Forwarding

Configuration:

```text
CCNRouter.forwardingMode = SPRAY_AND_WAIT
CCNRouter.nrofCopies = 10
CCNRouter.binaryMode = true
```

Each newly created message receives a copy-count property.

In Spray-and-Wait mode:

- Only messages with more than one remaining copy are sprayed.
- Binary mode divides copies between sender and receiver.
- Non-binary mode reduces the sender's copy count one at a time.
- The Spray-and-Wait copy mechanism is only active when the selected forwarding mode is `SPRAY_AND_WAIT`.

Example:

```text
Initial copies = 10
Binary split
       |
       +--> sender keeps part
       |
       +--> receiver gets part
```

---
 
 

# 7. Message Removal and Suppression

Several different events can cause a message/state entry to be removed.

## Duplicate Interest

If an Interest has already been processed:

```text
Duplicate Interest
      ->
Suppress current incoming ONE message
```

## Cache Hit

If Static Cache or Opportunistic Cache contains the requested content:

```text
Interest
   ->
Cache hit
   ->
Generate Data
   ->
Suppress the received Interest
```

## PIT Aggregation

If a matching Interest is already pending:

```text
New matching Interest
       ->
Add requester to PIT
       ->
Do not forward another copy
```

## PIT Satisfaction

When matching Data arrives:

```text
Data
  ->
Remove PIT entry
  ->
Generate response for pending requester(s)
```

## Duplicate Data

Already processed Data is suppressed.

## Processed-List Eviction

When duplicate-history state grows beyond:

```text
processedListCapacity
```

the oldest remembered entry is removed.

This is different from deleting actual cached content.

---

# 8. Main Configuration Parameters

| Setting | Purpose | Current Example |
|---|---|---:|
| `Group.router` | Router class used by nodes | `CCNRouter` |
| `CCNRouter.forwardingMode` | Select DTN forwarding algorithm | `PROPHET` |
| `CCNRouter.nrofCopies` | Initial Spray-and-Wait copies | `10` |
| `CCNRouter.binaryMode` | Binary Spray-and-Wait split | `true` |
| `CCNRouter.processedListCapacity` | Duplicate-history capacity | `500` |
| `CCNRouter.interestSize` | Simulated Interest message size | `50` |
| `CCNRouter.contentSize` | Simulated Data message size | `150` |
| `CCNRouter.pitTimeout` | PIT lifetime | `600` |
| `GroupX.cacheCapacity` | Opportunistic cache capacity for group | Group dependent |
| `GroupX.staticCacheCapacity` | Static cache capacity | Group dependent |
| `GroupX.avaCache` | Enable/disable static and opportunistic caches | e.g. `1,1` or `0,1` |
| `GroupX.enablePIT` | Enable PIT | `true` |
| `GroupX.mode` | Consumer/Producer/Intermediate role | `1`, `2`, or `3` |
| `CCNRouter.secondsInTimeUnit` | PRoPHET aging time unit | `30` |
 
---

# 9. Example Configuration

## PRoPHET Run

```text
Group.router = CCNRouter

CCNRouter.forwardingMode = PROPHET

CCNRouter.processedListCapacity = 500
CCNRouter.interestSize = 50
CCNRouter.contentSize = 150
CCNRouter.pitTimeout = 600

CCNRouter.secondsInTimeUnit = 30 
```

## Spray-and-Wait Run

```text
Group.router = CCNRouter

CCNRouter.forwardingMode = SPRAY_AND_WAIT
CCNRouter.nrofCopies = 10
CCNRouter.binaryMode = true
```

## Epidemic Run

```text
Group.router = CCNRouter

CCNRouter.forwardingMode = EPIDEMIC
```

---

# 10. Group-Specific Forwarding

`CCNRouter` reads group-specific settings before falling back to the global `CCNRouter` namespace.

Therefore, it is possible to configure different CCN groups with different forwarding modes while keeping the router class as `CCNRouter`.

Example:

```text
Group.router = CCNRouter

Group7.forwardingMode = PROPHET
Group8.forwardingMode = SPRAY_AND_WAIT
Group9.forwardingMode = EPIDEMIC
```

However, for controlled comparative experiments, using one forwarding mode across all CCNRouter groups in a single run is usually easier to interpret.

---

# 11. Recommended Experimental Structure

For a clean comparison, run separate scenarios:

```text
Run 1: CCN + EPIDEMIC
Run 2: CCN + SPRAY_AND_WAIT
Run 3: CCN + PROPHET
```

Keep the remaining parameters unchanged when comparing the forwarding modes.

Useful result categories include:

- Delivery probability
- Delivered messages
- Relayed messages
- Dropped messages
- Removed messages
- Overhead ratio
- Average latency
- Median latency
- Hop count
- Buffer time
- Static cache hits/misses
- Opportunistic cache hits/misses
- PIT aggregation/drop statistics
- Duplicate suppression
- Query count
- Response count

---

# 12. Core Files / Components

The implementation is centered on:

```text
routing/
  CCNRouter.java
  ccnutil/
      InterestPacket
      DataPacket
      PIT
      PITEntry
      StaticCache
      OpportunisticCache
      CCNRouterStats
```

`CCNRouter.java` is the actual CCN-aware router used in the scenario.


---

# 13. Current Router Flow Summary

```text
                     +------------------+
                     |     Consumer     |
                     +--------+---------+
                              |
                              | Interest
                              v
                     +------------------+
                     |    CCNRouter     |
                     +--------+---------+
                              |
           +------------------+------------------+
           |                  |                  |
           v                  v                  v
     Static Cache       Opportunistic           PIT
                           Cache
           |                  |                  |
           +------------------+------------------+
                              |
                        cache/PIT miss
                              |
                              v
                 +--------------------------+
                 | DTN Forwarding Strategy  |
                 +--------------------------+
                    |        |         |
                    v        v         v
                Epidemic   Spray    PRoPHET
                              |
                              v
                        Encountered Node
                              |
                              v
                          Producer /
                      Intermediate Cache
                              |
                              | Data
                              v
                       PIT Satisfaction
                              |
                              v
                           Consumer
```

---


## Project Status

The project currently represents a **CCN-aware DTN forwarding framework** in which CCN functions remain in `CCNRouter` while the DTN carrier can be changed among Epidemic, Spray-and-Wait, and PRoPHET for comparative simulation experiments.
