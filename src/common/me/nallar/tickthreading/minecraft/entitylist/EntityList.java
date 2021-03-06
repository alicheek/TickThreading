package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.world.World;

/*
* Used to override World.loadedTileEntityList.
* */
public abstract class EntityList<T> extends ArrayList<T> {
	public final TickManager manager;
	public final ArrayList innerList;

	EntityList(World world, Field overriddenField, TickManager manager, ArrayList innerList) {
		this.manager = manager;
		this.innerList = innerList;
		overriddenField.setAccessible(true);
		try {
			//This should hopefully avoid leaving the world in a bad state if something goes wrong.
			this.addAll((Collection<? extends T>) overriddenField.get(world));
			overriddenField.set(world, this);
		} catch (Exception e) {
			throw new RuntimeException("Failed to override " + overriddenField.getName() + " in world " + world.getWorldInfo().getWorldName(), e);
		}
	}

	@Override
	public abstract boolean add(T t);

	@Override
	public abstract boolean remove(Object o);

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean changed = false;
		for (T t : c) {
			changed |= add(t);
		}
		return changed;
	}

	@Override
	public boolean removeAll(Collection c) {
		boolean changed = false;
		for (Object t : c) {
			changed |= remove(t);
		}
		return changed;
	}

	@Override
	public Iterator<T> iterator() {
		return (Iterator<T>) innerList.iterator();
	}

	@Override
	public ListIterator<T> listIterator() {
		return (ListIterator<T>) innerList.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		return (ListIterator<T>) innerList.listIterator(index);
	}

	@Override
	public boolean contains(Object o) {
		return innerList.contains(o);
	}

	@Override
	public void trimToSize() {
		innerList.trimToSize();
	}

	@Override
	public void ensureCapacity(final int minCapacity) {
		innerList.ensureCapacity(minCapacity);
	}

	@Override
	public boolean isEmpty() {
		return innerList.isEmpty();
	}

	@Override
	public int indexOf(final Object o) {
		return innerList.indexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return innerList.lastIndexOf(o);
	}

	@SuppressWarnings ("CloneDoesntCallSuperClone")
	@Override
	public Object clone() {
		return innerList.clone();
	}

	@Override
	public Object[] toArray() {
		return innerList.toArray();
	}

	@Override
	public <T1> T1[] toArray(final T1[] a) {
		return (T1[]) innerList.toArray(a);
	}

	@Override
	public T set(final int index, final T element) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void add(final int index, final T element) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public T remove(final int index) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void clear() {
		innerList.clear();
	}

	@Override
	public boolean addAll(final int index, final Collection<? extends T> c) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected void removeRange(final int fromIndex, final int toIndex) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int size() {
		return innerList.size();
	}

	@Override
	public T get(int index) {
		return (T) innerList.get(index);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return innerList.containsAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return innerList.retainAll(c);
	}

	@Override
	public String toString() {
		return innerList.toString();
	}
}
