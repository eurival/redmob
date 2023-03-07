package br.com.redemob.rocket.controller;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import br.com.redemob.rocket.api.domain.model.Arquivo;
import br.com.redemob.rocket.api.domain.model.Inscricao;
import br.com.redemob.rocket.api.domain.model.Pessoa;
import br.com.redemob.rocket.api.domain.model.Usuario;
import br.com.redemob.rocket.api.domain.model.enumeration.StatusIncricao;
import br.com.redemob.rocket.api.repository.CidadeRepository;
import br.com.redemob.rocket.api.repository.InscricaoRepository;
import br.com.redemob.rocket.api.repository.PessoaRepository;
import br.com.redemob.rocket.api.repository.UsuarioRepository;
import br.com.redemob.rocket.api.service.CadastroUsuarioService;
import br.com.redemob.rocket.dto.InscricaoDTO;
import br.com.redemob.rocket.dto.UsuarioDTO;

@Controller

public class InscricaoController {
	
	private InscricaoDTO inscricaodto = new InscricaoDTO();
    @Autowired
    private PessoaRepository pessoaRepository;
    
    @Autowired
    private CidadeRepository cidadeRepository;
    
    @Autowired
    private InscricaoRepository inscricaoRepository;
    
    @Autowired
    private CadastroUsuarioService cadastroUsuario;
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    @GetMapping("")
    public String listInscricoes(Model model) {
        model.addAttribute("inscricoes", inscricaoRepository.findAll());
        return "inscricao-list";
    }
    
	@GetMapping("/rocket/novainscricao")
	public String fazerInscricao( Model model) {
		model.addAttribute("inscricao",new InscricaoDTO());
        model.addAttribute("pessoas", pessoaRepository.findAll());
        model.addAttribute("cidades", cidadeRepository.findAll());
        model.addAttribute("senha","");
   
		
		return "inscricao-form";
	}

	@PostMapping("/rocket/salvarinscricao")
	public String salvarInscricao(@ModelAttribute("inscricao") InscricaoDTO inscricao, 
			@ModelAttribute("usuario") UsuarioDTO usuario,
            @RequestParam("comprovantes") MultipartFile[] comprovantes, @RequestParam("cidade.id") Long cidade_id, Model model) {
		
        List<Arquivo> listaArquivos = new ArrayList<>();
        
        Inscricao novainscricao = new Inscricao();
        
        BeanUtils.copyProperties(inscricao, novainscricao);
        novainscricao.setCidade(cidadeRepository.findById(cidade_id).get());

		// Verifica se essa pessoa tem alguma inscrição feita
		Pessoa pessoa;
		// busca a pessoa caso ela tenha cadastro
		pessoa = pessoaRepository.findByCpf(inscricao.getPessoa().getCpf());
		
		//verifica se essa pessoa já fez algum cadastro
		if (pessoa==null) {
			// cadastra a pessoa
			pessoa = new Pessoa();
			
			BeanUtils.copyProperties(inscricao.getPessoa(), pessoa);
			
			Pessoa novapessoa = pessoaRepository.save(pessoa); 
			
			//cria um novo usuário
			Usuario novousuario= new Usuario();
			novousuario.setPessoa(novapessoa);
			novousuario.setAdmin(false);
			usuario.setSenha(usuario.getSenha());
			novousuario=cadastroUsuario.salvar(novousuario);
			
			//atribui a pessao dona da inscrição
			novainscricao.setPessoa(novapessoa);
			
		}else { // pessoa possui inscrições cadastrada

			model.addAttribute("pessoa", pessoa);
			model.addAttribute("inscricoes", pessoa.getInscricoes());

			if (pessoa.getInscricoes().size() >= 2) { 
				// caso tenha duas inscrições não será possivel fazer nova inscrição, apenas verificar o status das mesas

				//redireciona para as lista de inscrições da pessoa
				model.addAttribute("erro", "Você já possui duas inscrições realizadas! Confira o Status.");
				return "inscricao-list" ;
				
			} else if (pessoa.getInscricoes().size() == 1){ 
				// tem uma inscrição cadastrada, agora é verificar se ela já foi analisada ou não
				
				List<Inscricao> insc = new ArrayList<>(pessoa.getInscricoes());
				
				if (! insc.get(0).getStatus().equals("REPROVADO")) { 
					// caso a inscrição ainda nao foi reprovada para fazer uma nova, direciona para a lista de inscrição para acompanhamento
					model.addAttribute("erro", "Você já possui uma inscriçao realizada! Confira o Status.");
					return "inscricao-list" ;
				}
				
			}
			
			novainscricao.setPessoa(pessoa);
		}
		
		//salva todos os arquivso anexados na pasta /arquivos no root da aplicação
        for (MultipartFile file : comprovantes) {
            if (file != null && !file.isEmpty()) {
            	Arquivo arquivo = new Arquivo();
                try {
                	String path = System.getProperty("user.dir");
                	File diretorio = new File(path);		
                	diretorio.mkdirs();
                    String caminhoImagem = path+"\\arquivos\\" + UUID.randomUUID().toString() + "-" + file.getOriginalFilename();;
                    file.transferTo(new File(caminhoImagem));
                    arquivo.setCaminhoArquivo(caminhoImagem);
                    arquivo.setInscricao(novainscricao);
                    arquivo.setNomeArquivo(caminhoImagem);
                    arquivo.setTipo(caminhoImagem.split("\\.")[1]);
                    listaArquivos.add(arquivo);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        novainscricao.setStatus("EM ANALISE");
        novainscricao.setDataInscricao(LocalDateTime.now());
        
        novainscricao.setArquivos(listaArquivos);
        inscricaoRepository.save(novainscricao);
        return "redirect:/rocket/inscricao-list";
        
	}
	
    @GetMapping("/rocket/listarinscricoes")
    public String listarinscricoes(@PathVariable("id") Long id, Model model) {
    
    	// carrega as inscrições por pessoa para visualização do usuário
    	List<Inscricao> listaInscricaoPorPessoa = inscricaoRepository.findAllByPessoaId(id);
    	model.addAttribute("inscricoes", listaInscricaoPorPessoa);
    	
    	return "redirect:/rocket/inscricao-list";
    	
    }
	

}
