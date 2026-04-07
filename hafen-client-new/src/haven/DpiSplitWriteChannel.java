package haven;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Splits the first {@code budget} outbound bytes into small TCP writes with tiny gaps.
 * Some networks classify TLS by the shape of the first ClientHello packet; fragmenting
 * the write can change on-the-wire segmentation (best-effort; not a guarantee).
 */
public final class DpiSplitWriteChannel implements ByteChannel {
    private final ByteChannel sk;
    private int budget;

    public DpiSplitWriteChannel(ByteChannel sk, int budget) {
	this.sk = sk;
	this.budget = Math.max(0, budget);
    }

    /** Wrapped channel (for {@link HavenDpiBypass#unwrapSocketChannel(ByteChannel)}). */
    public ByteChannel wrapped() {
	return sk;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
	return sk.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
	int total = 0;
	ThreadLocalRandom rnd = ThreadLocalRandom.current();
	while(budget > 0 && src.hasRemaining()) {
	    int room = src.remaining();
	    int chunk = Math.min(room, 8 + rnd.nextInt(41));
	    chunk = Math.min(chunk, budget);
	    if(chunk < 1)
		chunk = 1;
	    int lim = src.limit();
	    int end = src.position() + chunk;
	    src.limit(end);
	    int w = sk.write(src);
	    src.limit(lim);
	    budget -= w;
	    total += w;
	    if(w == 0)
		return total > 0 ? total : 0;
	    if(budget > 0 && src.hasRemaining()) {
		try {
		    Thread.sleep(0, rnd.nextInt(500, 2500));
		} catch(InterruptedException e) {
		    Thread.currentThread().interrupt();
		}
	    }
	}
	while(src.hasRemaining()) {
	    int w = sk.write(src);
	    total += w;
	    if(w == 0)
		return total;
	}
	return total;
    }

    @Override
    public void close() throws IOException {
	sk.close();
    }

    @Override
    public boolean isOpen() {
	return sk.isOpen();
    }
}
