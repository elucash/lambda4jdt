package com.github.elucash.lambda4jdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

class RegionDemarkator {

	private LinkedList<RegionHandle> regions = new LinkedList<RegionHandle>();
	private int startedOffset = -1;
	int initialOffset;
	int finalOffset;
	boolean wasZeroOffset = false;

	void start(int offset) {
		if (offset == 0) {
			if (wasZeroOffset)
				throw new IllegalStateException("zero offset");
			wasZeroOffset = true;
		}
		offset += initialOffset;
		if (startedOffset >= 0)
			throw new IllegalStateException("allready started");
		startedOffset = offset;
		started = true;
	}

	boolean started;

	RegionHandle end(int end) {
		end += initialOffset;

		if (startedOffset < 0 || end < startedOffset)
			throw new IllegalStateException("no start or illegal position");

		int offset = startedOffset;
		startedOffset = -1;
		started = false;
		return range(offset, end - offset);
	}

	private RegionHandle range(int offset, int length) {
		RegionHandle region = new RegionHandle(offset, length);
		regions.add(region);
		return region;
	}

	IRegion[] toProcessedArray(IRegion prefix, IRegion suffix) {
		ArrayList<IRegion> processedList = processToList();
		processedList.add(finalOffset, prefix);
		processedList.add(suffix);
		return processedList.toArray(new IRegion[processToList().size()]);

	}

	IRegion[] toProcessedArray() {
		return processToList().toArray(new IRegion[processToList().size()]);
	}

	private ArrayList<IRegion> processToList() {
		ArrayList<IRegion> result = new ArrayList<IRegion>();
		Collections.sort(regions);

		RegionHandle lastRegion = null;
		for (RegionHandle r : regions) {
			if (!r.mergeIfPossibleTo(lastRegion)) {
				addIfNotObsolete(result, lastRegion);
				lastRegion = r;
			}
		}
		addIfNotObsolete(result, lastRegion);
		return result;
	}

	private void addIfNotObsolete(List<IRegion> result, RegionHandle region) {
		if (region != null)
			if (region.correct())
				result.add(region.toRegion());
	}

	class RegionHandle implements Comparable<RegionHandle> {

		int offset;
		int length;

		RegionHandle(int offset, int length) {
			this.offset = offset;
			this.length = length;
		}

		void reveal() {
			regions.remove(this);
		}

		public int compareTo(RegionHandle o) {
			return offset - o.offset;
		}

		private boolean correct() {
			if (length == 0)
				return false;

			if (finalOffset > initialOffset) {
				if (offset >= finalOffset)
					return false;

				if (offset + length > finalOffset) {
					length = finalOffset - offset;
				}
			}

			return true;
		}

		private boolean mergeIfPossibleTo(RegionHandle r) {
			if (r == null)
				return false;
			// goes after region r and begins before or just right after region r end
			int endOfR = r.offset + r.length;
			if (offset >= r.offset && offset <= endOfR) {
				int requiredLength = offset + length;
				if (requiredLength > endOfR)
					r.length = requiredLength - r.offset;

				return true;
			}
			return false;
		}

		private Region toRegion() {
			return new Region(offset, length);
		}

		public String toString() {
			return "[" + offset + '+' + length + ']';
		}
	}
}
