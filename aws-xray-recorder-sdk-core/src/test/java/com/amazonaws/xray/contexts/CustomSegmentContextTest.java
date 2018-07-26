package com.amazonaws.xray.contexts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.SubsegmentImpl;
import com.amazonaws.xray.exceptions.SubsegmentNotFoundException;

public class CustomSegmentContextTest {

    class GlobalMapSegmentContext implements SegmentContext {

        private Map<Long, Entity> map = new HashMap<>();

        @Override
        public Subsegment beginSubsegment(AWSXRayRecorder recorder, String name) {
            Entity current = map.get(Thread.currentThread().getId());
            Segment parentSegment = current.getParentSegment();
            Subsegment subsegment = new SubsegmentImpl(recorder, name, parentSegment);
            subsegment.setParent(current);
            current.addSubsegment(subsegment);
            map.put(Thread.currentThread().getId(), subsegment);
            return subsegment;
        }

        @Override
        public void endSubsegment(AWSXRayRecorder recorder) {
            Entity current = map.get(Thread.currentThread().getId());
            if (current instanceof Subsegment) {
                Subsegment currentSubsegment = (Subsegment) current;
                if (currentSubsegment.end()) {
                    recorder.sendSegment(currentSubsegment.getParentSegment());
                } else {
                    if (recorder.getStreamingStrategy().requiresStreaming(currentSubsegment.getParentSegment())) {
                        recorder.getStreamingStrategy().streamSome(currentSubsegment.getParentSegment(), recorder.getEmitter());
                    }
                    map.put(Thread.currentThread().getId(), current.getParent());
                }
            } else {
                recorder.getContextMissingStrategy().contextMissing("Failed to end subsegment: subsegment cannot be found.", SubsegmentNotFoundException.class);
            }
        }

        @Override
        public Entity getTraceEntity() {
            return map.get(Thread.currentThread().getId());
        }

        @Override
        public void setTraceEntity(Entity entity) {
            map.put(Thread.currentThread().getId(), entity);
        }

        @Override
        public void clearTraceEntity() {
            map.put(Thread.currentThread().getId(), null);
        }
    }

    class GlobalMapSegmentContextResolver implements SegmentContextResolver {

        private GlobalMapSegmentContext gmsc = new GlobalMapSegmentContext();

        @Override
        public SegmentContext resolve() {
            return gmsc;
        }
    }

    @Before
    public void setupAWSXRay() {
        Emitter blankEmitter = Mockito.mock(Emitter.class);
        Mockito.doReturn(true).when(blankEmitter).sendSegment(Mockito.anyObject());
        Mockito.doReturn(true).when(blankEmitter).sendSubsegment(Mockito.anyObject());

        LocalizedSamplingStrategy defaultSamplingStrategy = new LocalizedSamplingStrategy();
        SegmentContextResolverChain chain = new SegmentContextResolverChain();
        chain.addResolver(new GlobalMapSegmentContextResolver());

        AWSXRay.setGlobalRecorder(AWSXRayRecorderBuilder.standard().withEmitter(blankEmitter).withSegmentContextResolverChain(chain).withSamplingStrategy(defaultSamplingStrategy).build());
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testGlobalMapSegmentContext() {
        Segment test = AWSXRay.beginSegment("test");


        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(new Integer(i));
        }

        list.parallelStream().forEach(e -> {
            AWSXRay.setTraceEntity(test);
            AWSXRay.createSubsegment("parallelPrint", (subsegment) -> {
            });
        });

        Assert.assertEquals(100, test.getTotalSize().intValue());

        AWSXRay.endSegment();
    }
}
