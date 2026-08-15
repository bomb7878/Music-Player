import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.decoder.JavaLayerException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Field;

/**
 * Custom audio device that supports volume control via the Java Sound API.
 * Uses reflection to access the private 'source' field from the parent class.
 */
public class MusicAudioDevice extends JavaSoundAudioDevice {
    private float targetGain = 1.0f;   // linear volume 0..1

    @Override
    public void open(AudioFormat format) throws JavaLayerException {
        super.open(format);
        applyGain();
    }

    /**
     * Set volume (0.0 – 1.0). Immediately applies to the active line.
     */
    public void setVolume(float volume) {
        if (volume < 0.0f) volume = 0.0f;
        if (volume > 1.0f) volume = 1.0f;
        this.targetGain = volume;
        applyGain();
    }

    public float getVolume() {
        return targetGain;
    }

    /** Apply the current gain to the active SourceDataLine. */
    private void applyGain() {
        SourceDataLine line = getSourceDataLine();
        if (line == null) return;

        try {
            // Prefer MASTER_GAIN (decibels)
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float min = control.getMinimum();
                float max = control.getMaximum();
                // Convert linear 0..1 to decibels (log scale)
                float db = (targetGain == 0) ? min : 20f * (float) Math.log10(Math.max(targetGain, 0.0001));
                float value = Math.min(Math.max(db, min), max);
                control.setValue(value);
                return;
            }
            // Fallback to linear VOLUME control
            if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl control = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                float min = control.getMinimum();
                float max = control.getMaximum();
                float value = Math.min(Math.max(targetGain, min), max);
                control.setValue(value);
                return;
            }
        } catch (Exception e) {
            System.err.println("Error applying volume:");
            e.printStackTrace();
        }
    }

    /**
     * Obtains the SourceDataLine via reflection, since it's private in the parent.
     */
    private SourceDataLine getSourceDataLine() {
        try {
            Field field = JavaSoundAudioDevice.class.getDeclaredField("source");
            field.setAccessible(true);
            return (SourceDataLine) field.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}