/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004, Refractions Research Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package net.refractions.udig.issues;

import java.io.IOException;
import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

import net.refractions.udig.issues.internal.IssuesActivator;
import net.refractions.udig.issues.listeners.IIssuesListListener;
import net.refractions.udig.issues.listeners.IssuesListEvent;
import net.refractions.udig.issues.listeners.IssuesListEventType;

import org.eclipse.ui.WorkbenchException;



/**
 * An in-memory issues list. All issues will be lost at shut down unless {@link #save()} is
 * called. This is a useful utility class for implementing other issues list
 * implementations. The Datastore issues list (internal implementation) for
 * example wraps this list and uses it to cache all of its issues.
 * <p>
 * Notifies listeners when issues are added or removed from list.
 * </p>
 * 
 * @author jones
 * @since 1.0.0
 */
public class IssuesList extends AbstractSequentialList<IIssue> implements IIssuesList{

    LinkedList<IIssue> list=new LinkedList<IIssue>();
    
	/** long serialVersionUID field */
	private static final long serialVersionUID = -7157333022429945548L;

    private static final String ID = "net.refractions.udig.issues.memory"; //$NON-NLS-1$

	private boolean notify = true;

    Set<String>ids=new CopyOnWriteArraySet<String>();
    volatile int nextID=0;
	private void checkID(IIssue i, int index){
        if( i.getId()==null || ids.contains(i.getId()) ){
            String newID=findNextID(index);
            i.setId(newID);
        }
        ids.add(i.getId());
	}
	private String findNextID(int index) {
        return "Issue."+nextID++; //$NON-NLS-1$
    }
    public boolean add(IIssue o) {
        checkID(o, list.size());
		list.add(o);
		notify(o, IssuesListEventType.ADD);
		return true;
	}

	 
	public void add(int index, IIssue element) {
        checkID(element, index);
		list.add(index, element);
		notify(element, IssuesListEventType.ADD);
	}

	 
	public boolean addAll(Collection<? extends IIssue> c) {
		try {
			notify = false;
            for( IIssue issue : c ) {
                add(issue);
            }
		} finally {
			notify = true;
		}

		notify(c, IssuesListEventType.ADD);
        return true;
	}

	 
	public boolean addAll(int index, Collection<? extends IIssue> c) {
        boolean doNotify=notify;
		try {
			notify = false;
            int i=index;
            for( IIssue issue : c ) {
                add(i++,issue);
            }
		} finally {
			notify = doNotify;
		}
		notify(c, IssuesListEventType.ADD);
		return true;
	}

	 
	public void addFirst(IIssue o) {
        add(0,o);
	}

	 
	public void addLast(IIssue o) {
        add(o);
	}

	 
	public IIssue remove() {
		IIssue issue = remove(0);
		return issue;
	}

	 
	public IIssue remove(int index) {
		IIssue issue = list.remove(index);
        ids.remove(issue);
		if (issue != null)
			notify(issue, IssuesListEventType.REMOVE);
		return issue;
	}

	 
	public boolean remove(Object o) {
		if (o instanceof IIssue) {
			IIssue issue = (IIssue) o;
			boolean v = list.remove(issue);
			if (v){
                ids.remove(issue.getId());
				notify(issue, IssuesListEventType.REMOVE);
            }
			return v;
		}
		return false;
	}

	 
	public boolean removeAll(Collection<?> c) {
		Collection<IIssue> i = new HashSet<IIssue>();
		for (Object object : c) {
			if (!(object instanceof IIssue))
				return false;
			i.add((IIssue) object);
		}
		boolean v;
		try {
			notify = false;
			for( IIssue issue : i ) {
                ids.remove(issue.getId());
            }
            v=list.removeAll(i);
		} finally {
			notify = true;
		}
		if (v)
			notify(i, IssuesListEventType.REMOVE);
		return v;
	}

	 
	public IIssue removeFirst() {
		return remove(0);
	}

	 
	public IIssue removeLast() {
        return remove(list.size()-1);
	}

	 
	public IIssue set(int index, IIssue element) {
		IIssue old = list.set(index, element);
        element.setId(old.getId());
		notify(old, IssuesListEventType.REMOVE);
		notify(old, IssuesListEventType.ADD);
		return old;
	}

	 
	public boolean retainAll(Collection<?> c) {
		List<IIssue> changed = new LinkedList<IIssue>();
		boolean modified = false;
		Iterator<IIssue> e = list.iterator();
		try {
			notify = false;
			while (e.hasNext()) {
				IIssue current = e.next();
				if (!c.contains(current)) {
					e.remove();
                    ids.remove(current.getId());
					modified = true;
					changed.add(current);
				}
			}
		} finally {
			notify = true;
		}
		notify(changed, IssuesListEventType.REMOVE);
		return modified;
	}

	@SuppressWarnings("unchecked")
	 
	public void clear() {
		List<IIssue> changed;
		try {
			notify = false;
			changed = (List<IIssue>) list.clone();
            ids.clear();
            nextID=0;
			list.clear();
		} finally {
			notify = true;
		}
		notify(changed, IssuesListEventType.REMOVE);
	}

	/**
	 * <b>This is public for tesing purposes only!!!!</b>
	 */
	public Collection<IIssuesListListener> listeners = new CopyOnWriteArraySet<IIssuesListListener>();

	private ReentrantLock issuesListLock = new ReentrantLock();

	public void addListener(IIssuesListListener listener) {
		issuesListLock.lock();
		try {
			listeners.add(listener);
		} finally {
			issuesListLock.unlock();
		}
	}

	public void removeListener(IIssuesListListener listener) {
		issuesListLock.lock();
		try {
			listeners.remove(listener);
		} finally {
			issuesListLock.unlock();
		}
	}

	protected void notify(Collection<? extends IIssue> changed,
			IssuesListEventType type) {
		if (!notify)
			return;
		
		IssuesListEvent issuesListEvent = new IssuesListEvent(changed, type);
		for (IIssuesListListener listener : listeners) {
            listener.notifyChange(issuesListEvent);
		}
	}

    /**
     * Notify listeners of a change to the list.
     *
     * @param changed issue that has changed.  
     * @param type Type of change.
     */
	public void notify(IIssue changed, IssuesListEventType type) {
		HashSet<IIssue> set = new HashSet<IIssue>();
		set.add(changed);
		notify(set, type);
	}

	public Set<String> getGroups() {
		Set<String> groups = new HashSet<String>();
		for (IIssue issue : list) {
			groups.add(issue.getGroupId());
		}
		return groups;
	}

	public List<IIssue> getIssues(String groupId) {
		List<IIssue> group = new LinkedList<IIssue>();
		for (IIssue issue : list) {
			if( groupId==null ){
				if( issue.getGroupId() == null ){
					group.add(issue);
				}
			}else if (groupId.equals(issue.getGroupId())){
				group.add(issue);
			}
		}
		return group;
	}

	public void removeIssues(String groupId) {
		if (groupId == null)
			return;
		LinkedList<IIssue> group = new LinkedList<IIssue>();
		for (IIssue issue : list) {
			if (groupId.equals(issue.getGroupId()))
				group.add(issue);
		}
		this.removeAll(group);
	}


    @Override
    public ListIterator<IIssue> listIterator( int index ) {
        return list.listIterator(index);
    }

    @Override
    public int size() {
        return list.size();
    }
    public String getExtensionID() {
        return ID;
    }
    /**
     * Sets it so that adds and removes will not raise notifications.
     *
     * @param notifyListeners true if notifications should be sent
     */
    public void setNotify( boolean notifyListeners ) {
        notify=notifyListeners;
    }
    /**
     * Loads the issues list from the .issues.xml file in the workspace
     */
	public void load() {
		IssuesListPersister persister = new IssuesListPersister(this, ".issues.xml"); //$NON-NLS-1$
		try {
			persister.load();
		} catch (WorkbenchException e) {
			IssuesActivator.log("Failed to load old issues because the memento could not be parsed", e); //$NON-NLS-1$
		} catch (IOException e) {
			IssuesActivator.log("Failed to load old issues because the file could not be read", e); //$NON-NLS-1$
		}
	}
    /**
     * Saves the issues list to the .issues.xml file in the workspace
     */
	public void save() {
		IssuesListPersister persister = new IssuesListPersister(this, ".issues.xml"); //$NON-NLS-1$
		try {
			persister.save();
		} catch (IOException e) {
			IssuesActivator.log("Failed to save issues because the file could not be written", e); //$NON-NLS-1$
		}
	}
    
}
