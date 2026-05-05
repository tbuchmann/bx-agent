package dev.bxagent.correspondence;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * Determines the best available identifier for an EObject at runtime,
 * independent of the concrete metamodel.
 */
public class ObjectIdentifier {

    public enum IdentificationStrategy {
        XMI_ID, URI_FRAGMENT, CONTENT_FINGERPRINT
    }

    public record IdentificationEntry(String identifier, IdentificationStrategy strategy) {}

    /**
     * Returns the best available identifier for an EObject.
     * Priority:
     *   1. XMI-ID (if Resource is an XMIResource and the ID is non-null)
     *   2. URI fragment via resource.getURIFragment(eObject)
     */
    public static IdentificationEntry identify(EObject obj) {
        Resource resource = obj.eResource();
        if (resource instanceof XMIResource xmi) {
            String id = xmi.getID(obj);
            if (id != null && !id.isEmpty()) {
                return new IdentificationEntry(id, IdentificationStrategy.XMI_ID);
            }
        }
        if (resource != null) {
            try {
                String fragment = resource.getURIFragment(obj);
                if (fragment != null && !fragment.isEmpty()) {
                    return new IdentificationEntry(fragment, IdentificationStrategy.URI_FRAGMENT);
                }
            } catch (Exception ignored) {
            }
        }
        // Fallback: use identity hash (no persistence guarantee)
        return new IdentificationEntry(
                String.valueOf(System.identityHashCode(obj)),
                IdentificationStrategy.CONTENT_FINGERPRINT);
    }
}
