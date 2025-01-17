/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/artipie/blob/master/LICENSE.txt
 */
package com.artipie.docker.perms;

import com.artipie.docker.http.Scope;
import com.artipie.security.perms.Action;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker permissions implementation. Docker permission has three defining parameters:
 * <li>name (artipie repository name)</li>
 * <li>resource name (or image name, on the adapter side it's obtained from the request line)</li>
 * <li>the set of action, see {@link DockerActions}</li>
 *
 * @since 0.18
 */
public final class DockerRepositoryPermission extends Permission  {

    /**
     * Wildcard symbol.
     */
    static final String WILDCARD = "*";

    /**
     * Required serial.
     */
    private static final long serialVersionUID = -2916435271451239611L;

    /**
     * Canonical action representation. Is initialized once on request
     * by {@link DockerRepositoryPermission#getActions()} method.
     */
    private String actions;

    /**
     * Resource (or image) name, wildcard is allowed here.
     */
    private final String resource;

    /**
     * Action mask.
     */
    private final transient int mask;

    /**
     * Constructs a permission with the specified name.
     *
     * @param name Name of repository
     * @param scope The scope
     */
    public DockerRepositoryPermission(final String name, final Scope scope) {
        this(name, scope.name(), scope.action().mask());
    }

    /**
     * Constructs a permission with the specified name.
     *
     * @param name Name of repository
     * @param resource Resource (or image) name
     * @param actions Actions list
     */
    public DockerRepositoryPermission(
        final String name, final String resource, final Collection<String> actions
    ) {
        this(name, resource, maskFromActions(actions));
    }

    /**
     * Ctor.
     * @param name Permission name
     * @param resource Resource name
     * @param mask Action mask
     */
    public DockerRepositoryPermission(
        final String name, final String resource, final int mask
    ) {
        super(name);
        this.resource = resource;
        this.mask = mask;
    }

    @Override
    public boolean implies(final Permission permission) {
        final boolean res;
        if (permission instanceof DockerRepositoryPermission) {
            final DockerRepositoryPermission that = (DockerRepositoryPermission) permission;
            res = (this.mask & that.mask) == that.mask && this.impliesIgnoreMask(that);
        } else {
            res = false;
        }
        return res;
    }

    @Override
    public boolean equals(final Object obj) {
        final boolean res;
        if (obj == this) {
            res = true;
        } else if (obj instanceof DockerRepositoryPermission) {
            final DockerRepositoryPermission that = (DockerRepositoryPermission) obj;
            res = that.getName().equals(this.getName())
                && that.resource.equals(this.resource)
                && that.mask == this.mask;
        } else {
            res = false;
        }
        return res;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getName().hashCode(), this.resource);
    }

    @Override
    public String getActions() {
        if (this.actions == null) {
            final StringJoiner joiner = new StringJoiner(",");
            if ((this.mask & DockerActions.PULL.mask()) == DockerActions.PULL.mask()) {
                joiner.add(DockerActions.PULL.name().toLowerCase(Locale.ROOT));
            }
            if ((this.mask & DockerActions.PUSH.mask()) == DockerActions.PUSH.mask()) {
                joiner.add(DockerActions.PUSH.name().toLowerCase(Locale.ROOT));
            }
            if ((this.mask & DockerActions.OVERWRITE.mask()) == DockerActions.OVERWRITE.mask()) {
                joiner.add(DockerActions.OVERWRITE.name().toLowerCase(Locale.ROOT));
            }
            this.actions = joiner.toString();
        }
        return this.actions;
    }

    @Override
    public PermissionCollection newPermissionCollection() {
        return new DockerRepositoryPermissionCollection();
    }

    /**
     * Check if this action implies another action ignoring mask. That is true if
     * - permissions names are equal, or this permission has wildcard name
     * - resources names are equal or this permission has wildcard resource
     * @param perm Permission to check for imply
     * @return True when implies
     */
    private boolean impliesIgnoreMask(final DockerRepositoryPermission perm) {
        // @checkstyle LineLengthCheck (5 lines)
        return (this.getName().equals(DockerRepositoryPermission.WILDCARD) || this.getName().equals(perm.getName()))
            && (this.resource.equals(DockerRepositoryPermission.WILDCARD) || this.resource.equals(perm.resource));
    }

    /**
     * Get key for collection.
     * @return Repo name and resource joined with :
     */
    private String key() {
        return String.join(":", this.getName(), this.resource);
    }

    /**
     * Calculate mask from action.
     * @param actions The set of actions
     * @return Integer mask
     */
    private static int maskFromActions(final Collection<String> actions) {
        int res = Action.NONE.mask();
        if (actions.isEmpty() || actions.size() == 1 && actions.contains("")) {
            res = Action.NONE.mask();
        } else if (actions.contains(DockerRepositoryPermission.WILDCARD)) {
            res = DockerActions.ALL.mask();
        } else {
            for (final String item : actions) {
                res |= DockerActions.maskByAction(item);
            }
        }
        return res;
    }

    /**
     * Collection of {@link DockerRepositoryPermission} objects.
     * @since 1.2
     */
    public static final class DockerRepositoryPermissionCollection extends PermissionCollection
        implements java.io.Serializable {

        /**
         * Required serial.
         */
        private static final long serialVersionUID = 5843247295984092155L;

        /**
         * Correction of the repository type permissions.
         * Key is `name:resource`, value is permission. All permission objects in
         * collection must be of the same type.
         * Not serialized; see serialization section at end of class.
         */
        private final transient ConcurrentHashMap<String, Permission> collection;

        /**
         * This is set to {@code true} if this DockerPermissionCollection
         * contains a repository permission with '*' as its permission name,
         * '*' as its resource name and {@link DockerActions#ALL} action.
         */
        private boolean any;

        /**
         * Create an empty DockerPermissionCollection object.
         * @checkstyle MagicNumberCheck (5 lines)
         */
        DockerRepositoryPermissionCollection() {
            this.collection = new ConcurrentHashMap<>(5);
            this.any = false;
        }

        @Override
        public void add(final Permission obj) {
            if (this.isReadOnly()) {
                throw new SecurityException(
                    "attempt to add a Permission to a readonly PermissionCollection"
                );
            }
            if (obj instanceof DockerRepositoryPermission) {
                final DockerRepositoryPermission perm = (DockerRepositoryPermission) obj;
                final String key = perm.key();
                this.collection.put(key, perm);
                if (DockerRepositoryPermissionCollection.anyActionAllowed(perm)) {
                    this.any = true;
                }
            } else {
                throw new IllegalArgumentException(
                    String.format("Invalid permissions type %s", obj.getClass())
                );
            }
        }

        @Override
        @SuppressWarnings("PMD.CyclomaticComplexity")
        public boolean implies(final Permission obj) {
            boolean res = false;
            //@checkstyle NestedIfDepthCheck (50 lines)
            if (obj instanceof DockerRepositoryPermission) {
                final DockerRepositoryPermission perm = (DockerRepositoryPermission) obj;
                if (this.any) {
                    res = true;
                } else {
                    Permission existing = this.collection.get(perm.key());
                    if (existing != null) {
                        res = existing.implies(perm);
                    }
                    if (!res) {
                        existing = this.collection.get(
                            String.join(":", perm.getName(), DockerRepositoryPermission.WILDCARD)
                        );
                        if (existing != null) {
                            res = existing.implies(perm);
                        }
                        if (!res) {
                            existing = this.collection.get(
                                String.join(":", DockerRepositoryPermission.WILDCARD, perm.resource)
                            );
                            if (existing != null) {
                                res = existing.implies(perm);
                            }
                            if (!res) {
                                existing = this.collection.get(
                                    String.join(
                                        ":",
                                        DockerRepositoryPermission.WILDCARD,
                                        DockerRepositoryPermission.WILDCARD
                                    )
                                );
                                if (existing != null) {
                                    res = existing.implies(perm);
                                }
                            }
                        }
                    }
                }
            }
            return res;
        }

        @Override
        public Enumeration<Permission> elements() {
            return this.collection.elements();
        }

        /**
         * Check if given permission allows any action.
         * @param perm Permission to check
         * @return True if name and resource equal wildcard and action is {@link DockerActions#ALL}
         */
        private static boolean anyActionAllowed(final DockerRepositoryPermission perm) {
            return perm.getName().equals(DockerRepositoryPermission.WILDCARD)
                && perm.resource.equals(DockerRepositoryPermission.WILDCARD)
                && perm.mask == DockerActions.ALL.mask();
        }

    }

}
