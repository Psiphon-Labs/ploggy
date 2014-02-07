# Ploggy Groups

## Motivation

Groups are a common [online social network function](http://en.wikipedia.org/wiki/Group_%28online_social_networking%29) and will be a core feature of the Ploggy platform.

Groups enable selective sharing, whether it be location sharing, message post and photo sharing, or other sharing features that may be built on top of the Ploggy platform. Selective sharing allows for fine-grain privacy control, but is also important for performance.

Groups are also a natural mechanism to use to introduce friends to each other.

## User Stories

* Johnny shares photos using Ploggy. Johnny creates a Family group and shares photos of his baby. Johnny also belongs to a Work group and there he occasionally shares photos related to his job. Johnny configures Ploggy to consume Mobile data for photo sharing only for his Family group.
* Jill shares her location using Ploggy. Members of her Friends groups can see her location on the weekend. Members of her Work group can see her location during work hours. Members of her Hobby group can see her current city, but not street address.
* James attends a conference and exchanges Ploggy identities with new contacts that he meets. Later, he will decide what he will share with these contacts by assigning them to groups. In the meantime, he can build a list of friends without any performance impact.
* Jane is Ploggy friends with Jacob. She can send him a private post.
* Jason is has exchanged Ploggy identities with each member of his team at work. He creates a Group with all of his team as members. Each member of the team receives a notification about the Group, a list of Group members to add as new friends, and all posts made to the Group.

## Use Cases Notes

### Create group
* Groups are owned by a single user, but shared with all members. Only the owner can edit the group.
* Select members from current friends.
* Locally, your group appears and is active immediately.
* Special case at UI level: send private posts to one friend. Creates a group with one member.

### Add friend to group
* New members will get all past posts (and new posts, going forward) for this group -- from each member.
 * Security consideration: when you post to a group, the group owner of the can cause any of your friends to see it.

### Remove friend from group
* Old members keep the posts they synchronized in the past. For these old members, the group becomes read-only. They choose when to fully delete the old group and its data.
* Other group members will continue to share with removed members until they sync the new group.
* The status of each member with respect to group sync is displayed (so peers can see if a member has not received the latest group update).

### Owner delete group
* Only the owner can delete the group.
* The owner syncs the deleted state with all members.
* After syncing this state, members keep the posts they synchronized in the past. For these members, the group becomes read-only. They choose when to fully delete the old group and its data.

### Receive new group
* Received group is immediately active: appears in group list, can post to group.
* Some group members may not yet be friends. Group UI displays a list of candidate friends which launches an add friend interface for these members. The banner remains in place while the state exists. Posts to the group are not sent to non-friends.

### Receive updated group object
* New member which are not friends: same functionality as receive-new-group case.

### Request to leave group
* Non-owner can choose to resign group membership.
* The member immediately deletes all group posts and stops pulling group data from friends.
* Eventually, the owner is informed, and the owner will edit the group to remove the member.

### Receive post for known group
* Display post in group in UI.

### Receive post for unknown group
* For example: Alice adds Bob and Carol to a group; Carol receives the group from Alice and adds a post; Bob receives the post from Carol before communicating with Alice.
* Such posts are stored in the local database.
* Not displayed in UI until group is received. Once groups is synced, object will display in group

### Delete friend who is a group owner
* (Also consider this the group-owner-lost-his-instance-and-has-no-backup case.)
* Groups for deleted friend enter a read-only state. Group and its posts are still displayed. Users choose when to fully delete the old group and its data.

### Delete friend who is own group member
* Perform remove-friend-from-group case for each group friend belongs to.
* Since friend won’t sync any more, friend won’t learn about lost membership.

### Delete friend who is a non-owned group member
* Ex-friend reverts to candidate friend.
* No notice is sent to owner.
* Nothing is synced with ex-friend.

## UI Changes

Groups will be central to the app experience and its navigation will change to reflect that. Some of what follows is based on the UI of top mobile messaging apps on the theory that users will be most comfortable with what’s already familiar/standard (specifically, displaying Groups in a list).

* The current top-level view tab bar (containing “Your Status”, “Friends”, “Messages”) will be removed.
* Top-level view navigation will be via an Action Bar [drop-down spinner](http://developer.android.com/guide/topics/ui/actionbar.html#Dropdown). (I like [this](http://stackoverflow.com/questions/17613912/styling-actionbar-spinner-navigation-to-look-like-its-title-and-subtitle) customization which keeps the app name in place). The top-level views will be “All Friends”, “Groups”, and “Your Status”.
* “Groups” will be a list view with each row displaying a summary of the group. When a group is selected, the view changes to the Group Detail view.
* The Group Details view will display the group name and tabs: “Members”, “Messages”, and “My Settings”. Group Members and Group Messages tabs will be lists and My Settings will resemble the current Settings view. In the future, we would add more tabs for e.g., “Shared Documents”.

## Synchronization Protocol Changes

### Goals

* Synchronize groups and posts between users. Only members of a group will synchronize the group and items in the group.
* Pre-existing objects within a group will be detected for download after a user is added to a group.
* A group representation or other object is only downloaded when it has changed.
* Support the group resign and delete use cases.
* In addition to supporting groups, this new protocol addresses two serious inefficiencies in the first prototype: too much data (all data) is transferred when there is a change; and too much polling causes network activity when not necessary.
* Peers can synchronize groups independently, in arbitrary order (enabling, for example, only certain groups to be synchronized while using mobile data).

### High-Level Design

* Group IDs are random integers.
* For each group it publishes, a peer maintains a logical group write sequence. The group representation is assigned the next group sequence number when it is edited. When a group is deleted, a tombstone is retained and is assigned the next sequence number.
* For each group it is a member of or publisher of, a peer maintains a logical post write sequence. Each post published to the group is assigned the next post sequence number when it is created/edited. When a post is deleted, a tombstone is retained and is assigned the next sequence number.

* __pull__ request
 * Sent to each peer on Engine start up.
 * __Alice__ sends:
   * _lastReceivedSequenceNumbers = ((Group1, GroupSeqNumber1, PostSeqNumber1), …, (GroupN, GroupSeqNumberN, PostSeqNumberN))_ for each group __Alice__ knows __Bob__ belongs to or publishes; and 
   * _groupsToResignMembership = (GroupX, …, GroupY)_ for each group __Alice__ knows __Bob__ belongs to or publishes and which __Alice__ has chosen to resign from.
 * __Bob__ validates the request and determines which group representations to send to __Alice__ based on the group sequence numbers
 * __Bob__ adds to the request list groups where __Bob__ is the group publisher, __Alice__ is a member and __Alice__ did not request or chose to resign from the group.
  * When __Bob__ is the group publisher, __Bob__ removes __Alice__ from groups __Alice__ has chosen to resign from. In either case, __Bob__ skips these groups when responding with updated posts.
  * __Bob__ responds with a stream of group and post data represented as JSON, using HTTP chunk encoding. For each group in requested order, the first data item is the new group representation, if it has changed and __Bob__ is the publisher; next is each new, changed, or deleted post for the group, which __Bob__ has published. Each item includes its write sequence number.
  * As __Alice__ receives the items in the response, she writes/overwrites/deletes her local copies and updates a last known sequence numbers she maintains for each group of __Bob__ that __Alice__ knows.

* __push__ request
 * Sent when a local group or post is updated.
 * __Alice__ sends _(Group, GroupSeqNumber)_ or _(Post, GroupId, PostSeqNumber)_.
 * __Bob__ receives the item in the request and writes/overwrites/deletes his local copy and updates a last known sequence number she maintains for each group of __Alice__ that __Bob__ knows. In the case where the new item’s _SeqNumber > LastReceivedSeqNo - 1_, __Bob__ triggers a pull from __Alice__ as an update may have been missed.

### Notes

* This protocol will sync oldest objects first. It may be preferable to sync in application-specific order (e.g., newest message first for chat).
* The same algorithm can be used for “special” groups: a public, all-friends group and one-to-one ad hoc groups for private messaging.
* No changes to the existing attachments (images, files) protocol. These items are downloaded individually, on demand, in any order.

## Security Limitations

* Removal of a group member: a peer stops syncing with a removed member as soon as the peer receives and processes the modified group (immediately if the owner). It’s possible that the removed member continues to receive group posts after the owner has removed the member but before every other member has received the group update. Also, this is a potential side-channel which allows the removed member to observe when each other member receives the group update from the owner.

* There is a possibility of timing attacks in the pull request. The pull requester may be able to use response times to test how many groups the peer belongs to; or to guess a group ID; or to check if a peer knows a given group ID. Note that these timing attacks did not apply to friend authentication, which is done at the transport level via TLS certificates.
