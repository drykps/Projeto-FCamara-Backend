package br.com.projetofcamara.projeto.service.impl;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.projetofcamara.projeto.controller.dto.ProdutoDto;
import br.com.projetofcamara.projeto.entity.Avaliacao;
import br.com.projetofcamara.projeto.entity.Cliente;
import br.com.projetofcamara.projeto.entity.Comercio;
import br.com.projetofcamara.projeto.entity.Endereco;
import br.com.projetofcamara.projeto.entity.ItemPedido;
import br.com.projetofcamara.projeto.entity.Pedido;
import br.com.projetofcamara.projeto.entity.Produto;
import br.com.projetofcamara.projeto.enums.StatusPedido;
import br.com.projetofcamara.projeto.enums.TipoDetentor;
import br.com.projetofcamara.projeto.exception.RegraDeNegocioException;
import br.com.projetofcamara.projeto.repository.PedidoRepository;
import br.com.projetofcamara.projeto.service.ClienteService;
import br.com.projetofcamara.projeto.service.ComercioService;
import br.com.projetofcamara.projeto.service.EnderecoService;
import br.com.projetofcamara.projeto.service.PedidoService;
import br.com.projetofcamara.projeto.service.ProdutoService;

@Service
public class PedidoServiceImpl implements PedidoService{
	
	@Autowired
	PedidoRepository pedidoRepository;
	
	@Autowired
	ComercioService comercioService;
	
	@Autowired
	ProdutoService produtoService;
	
	@Autowired
	EnderecoService enderecoService;
	
	@Autowired
	ClienteService clienteService;

	@Override
	public Optional<Pedido> criarPedido(Pedido pedido){
	
		if(pedido.getItensPedido().isEmpty()) {
			throw new RegraDeNegocioException("Carrinho vazio!");
		}
		
		Optional<Comercio> comercioBd = Optional.empty();
		
		Optional<Endereco> enderecoBd = enderecoService.buscarEnderecoPeloId(pedido.getEndereco().getId());
		Optional<Cliente> clienteBd = clienteService.buscarClientePeloId(pedido.getCliente().getId());		
		if(enderecoBd.isPresent() && clienteBd.isPresent() && !clienteBd.get().getId().equals(enderecoBd.get().getCodigoDetentor())) {
			throw new RegraDeNegocioException("Endereço não pertence ao cliente");
		}		
		int i = 0;
		for (ItemPedido itemPedido : pedido.getItensPedido()) {
			Optional<Produto> optionalProdutoBD = produtoService.buscarProdutoPeloId(itemPedido.getProduto().getId());
			
			if(optionalProdutoBD.isPresent()) {
				
				if(i == 0) {
					comercioBd = comercioService.buscarComercioPeloId(optionalProdutoBD.get().getComercio().getId());
					i++;
				}
				
				Produto produtoBd = optionalProdutoBD.get();
				
				if(!produtoBd.getComercio().getId().equals(comercioBd.get().getId())) {
					throw new RegraDeNegocioException("Produto " + produtoBd.getNome() + " de comercio diferente");					
				}						
				
				if(produtoBd.isProdutoDemanda() && !produtoBd.isProdutoDisponivel()) {					
					calculaSubtotalPedido(pedido, itemPedido, produtoBd);										
				}
				
				if(produtoBd.isProdutoEstoque()) {
					if(produtoBd.getQuantidade() - itemPedido.getQuantidade() < 0 || !produtoBd.isProdutoDisponivel()) {
						throw new RegraDeNegocioException("Produto sem estoque ou não possui a quantidade desejada");
					}
					itemPedido.setProduto(new ProdutoDto(produtoBd));
					
					calculaSubtotalPedido(pedido, itemPedido, produtoBd);
					subtraiQuantidadeProdutoEstoque(itemPedido, produtoBd);
				}
			}		
		}
		pedido.setComercio(comercioBd.get());
		pedido.setFrete(comercioBd.get().getValorEntrega());		
		pedido.setTotal(pedido.getSubtotal() + comercioBd.get().getValorEntrega());
		
		return Optional.ofNullable(pedidoRepository.save(pedido));			
	}

	private void calculaSubtotalPedido(Pedido pedido, ItemPedido itemPedido, Produto produtoBd) {
		itemPedido.setValorProduto(produtoBd.getPreco());
		pedido.setSubtotal( pedido.getSubtotal() + itemPedido.getValorProduto() * itemPedido.getQuantidade() );		
	}

	private void subtraiQuantidadeProdutoEstoque(ItemPedido itemPedido, Produto produto) {
		produto.setQuantidade(produto.getQuantidade() - itemPedido.getQuantidade());
		produtoService.alterarEstoqueProduto(produto);
	}	

	@Override
	public Optional<Pedido> buscarPedidoId(String id) {
		return pedidoRepository.findById(id);
	}
	
	@Override
	public List<Pedido> listarPedidoCliente(String idCliente) {
		return pedidoRepository.findByCliente(new Cliente(idCliente));
	}

	@Override
	public List<Pedido> listarPedidoComercio(String idComercio) {		
		return pedidoRepository.findByComercio(new Comercio(idComercio));
	}
	
	public Optional<Pedido> aceitaPedido(Pedido pedido){
		
		Optional<Pedido> pedidoBd = pedidoRepository.findById(pedido.getId());
		if(pedidoBd.isPresent() && StatusPedido.PENDENTE.equals( pedidoBd.get().getStatusPedido() )) {
			pedidoBd.get().setStatusPedido(pedido.getStatusPedido());	
		}else{
			throw new RegraDeNegocioException("Este pedido foi cancelado/negado");
		}
		return Optional.ofNullable(pedidoRepository.save(pedidoBd.get()));			
	}	

	public Optional<Pedido> negarCancelarPedido(Pedido pedido){
		
		Optional<Pedido> pedidoBd = pedidoRepository.findById(pedido.getId());
		if(pedidoBd.isPresent() && StatusPedido.PENDENTE.equals( pedidoBd.get().getStatusPedido() )) {
			pedidoBd.get().setStatusPedido(pedido.getStatusPedido());			
		}else {
			throw new RegraDeNegocioException("Pedido só pode ser negado/cancelado se estiver pendente");
		}
		return Optional.ofNullable(pedidoRepository.save(pedidoBd.get()));					
	}
	
	public Optional<Pedido> enviarPedido(Pedido pedido){
		
		Optional<Pedido> pedidoBd = pedidoRepository.findById(pedido.getId());
		if(pedidoBd.isPresent() && StatusPedido.ACEITO.equals( pedidoBd.get().getStatusPedido() )) {
			pedidoBd.get().setStatusPedido(pedido.getStatusPedido());	
		}else {
			throw new RegraDeNegocioException("Pedido só pode estar com status enviado se foi aceito");
		}
		return Optional.ofNullable(pedidoRepository.save(pedidoBd.get()));			
	}
	
	public Optional<Pedido> entregarPedido(Pedido pedido){
		
		Optional<Pedido> pedidoBd = pedidoRepository.findById(pedido.getId());
		if(pedidoBd.isPresent() && StatusPedido.ENVIADO.equals( pedidoBd.get().getStatusPedido() )) {
			pedidoBd.get().setStatusPedido(pedido.getStatusPedido());	
		}else {
			throw new RegraDeNegocioException("Pedido só pode estar com status entregue se foi enviado");
		}
		return Optional.ofNullable(pedidoRepository.save(pedidoBd.get()));			
	}		
	
	@Override
	public Optional<Pedido> criarAvaliacao(Avaliacao avaliaPedido, String avaliado, String idPedido) {
		
		Optional<Pedido> pedidoBd = pedidoRepository.findById(idPedido);		
		if(!pedidoBd.isPresent()) {
			throw new RegraDeNegocioException("Este pedido não existe");
		}
		
		Optional<Comercio> comercioBd = comercioService.buscarComercioPeloId(pedidoBd.get().getComercio().getId());
		if(!comercioBd.isPresent()) {
			throw new RegraDeNegocioException("comercio não existe");
		}
		
		Optional<Cliente> clienteBd = clienteService.buscarClientePeloId(pedidoBd.get().getCliente().getId());
		if(!clienteBd.isPresent()) {
			throw new RegraDeNegocioException("cliente não existe");
		}
		
		if(TipoDetentor.CLIENTE.equals(TipoDetentor.getEnumFromText(avaliado))) {
			
			avaliaPedido.setCodigoAvaliado(pedidoBd.get().getCliente().getId());
			avaliaPedido.setCodigoAvaliador(pedidoBd.get().getComercio().getId());
			pedidoBd.get().setAvaliacaoDoCliente(avaliaPedido);
			
			clienteService.novaAvaliacao(avaliaPedido);
		}
		
		if(TipoDetentor.COMERCIO.equals(TipoDetentor.getEnumFromText(avaliado))) {
			
			avaliaPedido.setCodigoAvaliado(pedidoBd.get().getComercio().getId());
			avaliaPedido.setCodigoAvaliador(pedidoBd.get().getCliente().getId());	
			pedidoBd.get().setAvaliacaoDoComercio(avaliaPedido);

			comercioService.novaAvaliacao(avaliaPedido);
		}
				
		return Optional.ofNullable(pedidoRepository.save(pedidoBd.get()));
	}
	
}
