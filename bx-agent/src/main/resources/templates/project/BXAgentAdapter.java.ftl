package ${adapterPackage};

import java.io.IOException;
import java.util.function.Supplier;

import org.benchmarx.config.Configurator;
import org.benchmarx.edit.IEdit;
import org.benchmarx.emf.BXToolForEMF;
import ${decisionsPackage}.Decisions;
import ${sourceComparatorPackage}.${sourceComparatorClass};
import ${targetComparatorPackage}.${targetComparatorClass};
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import ${sourcePackageName}.*;
import ${targetPackageName}.*;
import ${basePackage}.${generatedClassName};
import dev.bxagent.correspondence.CorrespondenceModel;
import dev.bxagent.correspondence.TransformationContext;

public class ${adapterClassName}
        extends BXToolForEMF<${sourcePackageName}.${rootSourceType}, ${targetPackageName}.${rootTargetType}, Decisions> {

    private ResourceSet set = new ResourceSetImpl();
    private Resource source;
    private Resource target;
    private Resource corr;

    private Configurator<Decisions> conf;
    private Configurator<Decisions> defaultConf;

    private static final String RESULTPATH = "results/bxagent";

    public ${adapterClassName}() {
        super(new ${sourceComparatorClass}(), new ${targetComparatorClass}());
    }

    @Override
    public void initiateSynchronisationDialogue() {
        set.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put("xmi", new XMIResourceFactoryImpl());

        source = set.createResource(URI.createURI("${sourcePackageName}.xmi"));
        target = set.createResource(URI.createURI("${targetPackageName}.xmi"));

        ${rootSourceType} sourceRoot = ${sourceFactory}.eINSTANCE.create${rootSourceType}();
        ${rootTargetType} targetRoot = ${targetFactory}.eINSTANCE.create${rootTargetType}();
        source.getContents().add(sourceRoot);
        target.getContents().add(targetRoot);

        ${generatedClassName}.transform(source, target);

        // Use in-memory URI so saveAndUpdateTimestamp skips XMI serialization (avoids O(n²) cost)
        URI corrURI = URI.createURI("memory://correspondence.corr.xmi");
        corr = CorrespondenceModel.loadOrCreate(corrURI, set);
    }

    @Override
    public void terminateSynchronisationDialogue() {
        // No file to delete — correspondence model is held in memory only (memory:// URI).
        corr = null;
        set = new ResourceSetImpl();
    }

    @Override
    public void setConfigurator(Configurator<Decisions> configurator) {
        if (defaultConf == null) defaultConf = configurator;
        conf = configurator;
    }

    @Override
    public ${sourcePackageName}.${rootSourceType} getSourceModel() {
        return (${sourcePackageName}.${rootSourceType}) source.getContents().get(0);
    }

    @Override
    public ${targetPackageName}.${rootTargetType} getTargetModel() {
        return (${targetPackageName}.${rootTargetType}) target.getContents().get(0);
    }

    @Override
    public void saveModels(String name) {
        ResourceSet saveSet = new ResourceSetImpl();
        saveSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        URI srcURI = URI.createFileURI(RESULTPATH + "/" + name + "_src.xmi");
        URI trgURI = URI.createFileURI(RESULTPATH + "/" + name + "_tgt.xmi");
        Resource resSrc = saveSet.createResource(srcURI);
        Resource resTgt = saveSet.createResource(trgURI);
        resSrc.getContents().add(org.eclipse.emf.ecore.util.EcoreUtil.copy(getSourceModel()));
        resTgt.getContents().add(org.eclipse.emf.ecore.util.EcoreUtil.copy(getTargetModel()));
        try {
            resSrc.save(null);
            resTgt.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void performAndPropagateSourceEdit(
            Supplier<IEdit<${sourcePackageName}.${rootSourceType}>> sourceEdit) {
        sourceEdit.get();
        ${generatedClassName}.transform(source, target, corr,
            TransformationContext.DeletionPolicy.TOMBSTONE);
    }

    @Override
    public void performAndPropagateTargetEdit(
            Supplier<IEdit<${targetPackageName}.${rootTargetType}>> targetEdit) {
        targetEdit.get();
<#if hasOptions>
        // TODO: map conf.decide(Decisions.X) to the appropriate option values.
        // Available backward parameters:
<#list backwardConfigs as bc>
        //   ${bc.parameterName()} (${bc.parameterType()}, default: ${bc.defaultValue()}) — ${bc.description()}
</#list>
        ${generatedClassName}.Options opts = ${generatedClassName}.Options.defaults();
        ${generatedClassName}.transformBack(target, source, corr,
            TransformationContext.DeletionPolicy.TOMBSTONE, opts);
<#else>
        ${generatedClassName}.transformBack(target, source, corr,
            TransformationContext.DeletionPolicy.TOMBSTONE);
</#if>
    }

    @Override
    public void performIdleSourceEdit(
            Supplier<IEdit<${sourcePackageName}.${rootSourceType}>> edit) {
        edit.get();
    }

    @Override
    public void performIdleTargetEdit(
            Supplier<IEdit<${targetPackageName}.${rootTargetType}>> edit) {
        edit.get();
    }

    @Override
    public void performAndPropagateEdit(
            Supplier<IEdit<${sourcePackageName}.${rootSourceType}>> sourceEdit,
            Supplier<IEdit<${targetPackageName}.${rootTargetType}>> targetEdit) {
        sourceEdit.get();
        targetEdit.get();
        ${generatedClassName}.sync(source, target, corr);
    }

    @Override
    public String getName() {
        return "BXAgent";
    }

    @Override
    public String toString() {
        return getName();
    }
}
