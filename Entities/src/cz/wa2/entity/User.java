package cz.wa2.entity;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

@Entity
@Table(name = "User")
public class User {

	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	
	@Column(name = "email")
	private String email;
	
	@Column(name = "email_hash")
	private String emailHash;
	
	@Column(name = "fqn")
	private String fqn;
	
	@ManyToMany(mappedBy = "candidates", fetch = FetchType.LAZY)
	private List<cz.wa2.entity.Error> errors;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
	public String getEmailHash() {
		return emailHash;
	}

	public void setEmailHash(String emailHash) {
		this.emailHash = emailHash;
	}

	public String getFqn() {
		return fqn;
	}

	public void setFqn(String fqn) {
		this.fqn = fqn;
	}

	@Override
	public String toString() {
		return fqn + "(" + email + ")";
	}

	public List<cz.wa2.entity.Error> getErrors() {
		return errors;
	}

	public void setErrors(List<cz.wa2.entity.Error> errors) {
		this.errors = errors;
	}
	
	

}
