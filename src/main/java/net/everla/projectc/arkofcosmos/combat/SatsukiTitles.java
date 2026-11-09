package net.everla.projectc.arkofcosmos.combat;

/**
 * Runtime hook for SatsukiTitleTransformer. GR grants title k's ability with "title == k || title == 12"
 * (万众归一); for Satsuki, 飞翔于空中 (13) reads as 12 at exactly those sites, so both of her final stages hold
 * every other stage's ability except 不老不死 (0). Title 12/13's own checks are untouched. No optional-mod types.
 */
public final class SatsukiTitles {
    private SatsukiTitles(){}
    public static int unify(int title,Object owner){return title==13&&owner instanceof SatsukiBoss?12:title;}
}
